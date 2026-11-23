import com.smartfactory.energy.recommendation.EssLimits;
import com.smartfactory.energy.recommendation.EssPlanner;
import com.smartfactory.energy.recommendation.PlanInput;
import com.smartfactory.energy.tariff.TariffPeriodType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1년 15분 칸마다 실제 백엔드 판단 엔진(EssPlanner)의 plan + decide를 그대로 불러
 * "ESS + WATTever 추천(자동 모드: 추천을 모두 적용)"을 돌린다. 백엔드 코드는 고치지 않고 컴파일된 클래스만 씀.
 *
 * 칸 i마다 (RecommendationEngine.run과 같은 순서)
 *   0) AI 예측 오차 보정: 지난 1시간(4칸) 동안 예측이 빗나간 평균만큼 앞으로의 예측을 올리거나 내림
 *   1) 지금 칸부터 96칸 계획: 부하 = 보정한 AI 예측(평균 / 상위 90%), 태양광 = 실제 값, 단가 = 요금표
 *   2) 지금 추천: 이번 15분의 실제 부하를 보고 decide (15분 안에서는 부하가 일정하다고 봄)
 *   3) 추천 출력 적용 → 한전 구매, 잔량 갱신 (방전은 역송 없게, 잔량은 하한~상한)
 *
 * 사용법: java -cp <backend classes>;. EssYearSim <입력 csv> <출력 csv> [목표 피크]
 */
public class EssYearSim {

    public static void main(String[] args) throws Exception {
        double target = args.length > 2 ? Double.parseDouble(args[2]) : 1800;
        // backend: 지금 백엔드 그대로 (AI 예측 + 오차 보정) / nobias: 오차 보정 없이 AI 예측만
        // perfect: 부하를 미리 정확히 안다고 봄 (예측 = 실제, 이 배터리로 낼 수 있는 최대치 확인용)
        String mode = args.length > 3 ? args[3] : "backend";
        // 환경 설정 기본값과 같음: 주의선 97%, 최대 출력 500 kW, 용량 1,000 kWh, 잔량 20~90%, 효율 89.5%
        EssLimits limits = new EssLimits(target, target * 0.97, 500, 1000, 20, 90, 89.5);

        List<String> ts = new ArrayList<>();
        List<double[]> rows = new ArrayList<>(); // load, mean, p90, solar, price
        List<String> periods = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(args[0], StandardCharsets.UTF_8))) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] c = line.split(",");
                ts.add(c[0]);
                rows.add(new double[] {Double.parseDouble(c[1]), Double.parseDouble(c[2]),
                        Double.parseDouble(c[3]), Double.parseDouble(c[4]), Double.parseDouble(c[5])});
                periods.add(c[6]);
            }
        }
        int n = rows.size();
        EssPlanner planner = new EssPlanner();
        double soc = 50; // 시작 잔량 (판단 엔진의 끝 잔량 기준과 같음)
        long started = System.currentTimeMillis();

        try (PrintWriter w = new PrintWriter(args[1], StandardCharsets.UTF_8)) {
            w.println("ts,essKw,gridKw,socAfter,reason,lineKw,marginKw");
            for (int i = 0; i < n; i++) {
                // 0) AI 예측 오차 보정 (LoadForecastService.fromAi와 같음): 지난 4칸의 (실제 부하 − 평균 예측) 평균
                double errorSum = 0;
                int errorCount = 0;
                for (int k = 1; k <= 4 && i - k >= 0; k++) {
                    double[] past = rows.get(i - k);
                    errorSum += past[0] - past[1];
                    errorCount++;
                }
                double biasKw = errorCount > 0 && mode.equals("backend") ? errorSum / errorCount : 0;

                // 1) 96칸 계획 (연말을 넘는 칸은 1월 1일로 이어 붙임: 해마다 같은 모양이 반복된다고 봄)
                List<PlanInput> inputs = new ArrayList<>(96);
                for (int k = 0; k < 96; k++) {
                    int j = (i + k) % n;
                    double[] v = rows.get(j);
                    double loadKw = mode.equals("perfect") ? v[0] : Math.max(0, v[1] + biasKw);
                    double peakKw = mode.equals("perfect") ? v[0] : Math.max(loadKw, v[2] + biasKw);
                    inputs.add(new PlanInput(LocalDateTime.parse(ts.get(j)), loadKw, peakKw, v[3], v[4], typeOf(periods.get(j))));
                }
                EssPlanner.PlanResult plan = planner.plan(inputs, soc, limits);

                // 2) 지금 추천: 이번 15분 실제 (부하 − 태양광)을 보고 판단
                double[] now = rows.get(i);
                double actualNet = now[0] - now[3];
                EssPlanner.NowInfo info = new EssPlanner.NowInfo(0, actualNet, 15, soc);
                EssPlanner.Advice advice = planner.decide(plan, info, inputs.get(0).netKw(), limits);

                // 3) 적용 (시뮬레이터와 같게 방전은 부하 − 태양광까지만, 잔량은 하한~상한)
                double ess = advice.wantKw();
                if (ess > 0) {
                    ess = Math.min(ess, Math.max(0, actualNet));
                }
                double next = soc + limits.socChangeOf(ess);
                if (next < limits.socLowPercent() && ess > 0) {
                    ess = (soc - limits.socLowPercent()) / 100 * limits.capacityKwh() / 0.25;
                    next = limits.socLowPercent();
                } else if (next > limits.socHighPercent() && ess < 0) {
                    ess = -(limits.socHighPercent() - soc) / 100 * limits.capacityKwh() / 0.25
                            / (limits.efficiencyPercent() / 100);
                    next = limits.socHighPercent();
                }
                double grid = Math.max(0, actualNet - ess);
                soc = next;
                w.printf("%s,%.3f,%.3f,%.3f,%s,%.1f,%.1f%n", ts.get(i), ess, grid, soc,
                        advice.reasonType(), plan.lineKw(), plan.marginKw());

                if (i % 2880 == 0) {
                    System.out.printf("%s 까지 (%.0f초)%n", ts.get(i), (System.currentTimeMillis() - started) / 1000.0);
                }
            }
        }
        System.out.printf("끝: %d칸, %.0f초, 마지막 잔량 %.1f%%%n", n, (System.currentTimeMillis() - started) / 1000.0, soc);
    }

    private static TariffPeriodType typeOf(String period) {
        return switch (period) {
            case "LIGHT" -> TariffPeriodType.OFF_PEAK;
            case "MID" -> TariffPeriodType.MID_PEAK;
            default -> TariffPeriodType.ON_PEAK;
        };
    }
}
