package com.smartfactory.energy.recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ESS 추천 계산. 넘겨받은 값만 보고 답을 내고, DB는 보지 않습니다. (같은 값을 넣으면 항상 같은 답)
 * 프론트 가짜 백엔드의 frontend/src/api/fakeRecommendation.js를 옮긴 것이고, 설명은 frontend/docs/ess-recommendation.md
 *
 * 1) plan: 지금 칸부터 24시간(15분 96칸) 충전·방전·대기 계획 (동적 계획법)
 * 2) decide: 계획과 이번 15분 구간의 실제 전력을 보고 지금 무엇을 추천할지
 * 3) peakRiskOf / explain: 추천 카드에 보이는 이유 문장
 *
 * 프론트 버전과 다른 점
 * - 계획 구간: "지금부터 자정까지" → "지금부터 24시간" (이동 구간). 끝 잔량 기준(50%)은 24시간 뒤에 적용
 * - 남는 태양광: 배터리에 다 못 담으면 버려지는 것으로 봄 (예전에는 이런 칸이 있으면 계획을 못 짰음)
 */
@Component
public class EssPlanner {

    // 계획 끝(24시간 뒤)에 남겨 둘 잔량. 모자라면 그만큼 경부하에 다시 채운다고 보고 비용에 더함
    static final double END_SOC_PERCENT = 50;
    // 1 kWh 방전할 때마다 배터리가 닳는 값 (임시값, 팀 확인 필요)
    static final double WEAR_COST_WON_PER_KWH = 20;
    // 예비 잔량: 실제 부하가 예측보다 이만큼 더 나와도 막을 수 있게 남겨 둠 (프론트 1년 시뮬레이션으로 정한 값)
    static final double RESERVE_EXTRA_KW = 200;
    // 예비 잔량 1 kWh가 한 칸 동안 모자랄 때의 벌점 (원)
    static final double RESERVE_PENALTY_WON = 300;
    // 이보다 작은 충·방전은 추천하지 않음
    static final double MIN_ADVICE_KW = 10;

    private static final double SLOT_HOURS = 0.25;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    // 계획 결과. essKw: 칸마다 ESS 출력 (+ 방전 / − 충전)
    public record PlanResult(double lineKw, double marginKw, double[] essKw) {
    }

    // 이번 15분 구간의 지금 상황
    public record NowInfo(
            double soFarKw,            // 이번 구간에 지금까지 쌓인 평균 (한전 구매 합 ÷ 15)
            double recentWithoutEssKw, // 최근 몇 분 동안 ESS 없이 한전에서 샀을 전력 (부하 − 태양광)
            double remainingMinutes,   // 구간 남은 분
            double socPercent          // 지금 잔량 %
    ) {
        // ESS를 essKw로 돌리면 이번 구간 평균이 얼마가 될지
        public double predictedOf(double essKw) {
            return soFarKw + (recentWithoutEssKw - essKw) * remainingMinutes / 15;
        }
    }

    // 지금 추천할 것. wantKw: 낼 출력 (+ 방전 / − 충전)
    public record Advice(RecommendationReasonType reasonType, RecommendationAction action, double wantKw) {
    }

    // 다음 피크 위험. startAt: 첫 위험 칸 (없으면 null), needPercent: 위험 칸을 다 막는 데 필요한 잔량
    public record PeakRisk(LocalDateTime startAt, double peakKw, boolean nowInRisk, double needPercent) {
    }

    // ---------- 1) 계획 ----------

    // 계산에 같이 쓰는 값들
    private record Setup(int low, int states, int start, double kwhPerPercent, double efficiency,
                         List<Integer> changes, double cheapest) {
    }

    // 칸마다 충전·방전·대기를 정해서, 아래 비용의 합이 가장 작은 계획을 찾음
    //   비용 = 전력량요금 (한전에서 산 전기 × 그 시각 단가) + 배터리 마모 비용 + 예비 잔량이 모자란 벌점
    //   지킬 것 = 모든 칸에서 한전 구매 ≤ 방어선, 잔량은 하한~상한, 충·방전은 최대 출력까지
    // 표 채우기(동적 계획법): 가로 = 칸(시각), 세로 = 잔량(1% 단위)
    public PlanResult plan(List<PlanInput> inputs, double startSoc, EssLimits limits) {
        double efficiency = limits.efficiencyPercent() / 100;
        double kwhPerPercent = limits.capacityKwh() / 100;
        int low = (int) Math.ceil(limits.socLowPercent());
        int states = (int) Math.floor(limits.socHighPercent()) - low + 1;
        int start = Math.min(states - 1, Math.max(0, (int) Math.round(startSoc) - low));
        // 한 칸(15분)에 잔량이 줄거나 늘 수 있는 최대 (%)
        int maxDown = (int) Math.floor(limits.maxPowerKw() * SLOT_HOURS / kwhPerPercent);
        int maxUp = (int) Math.floor(limits.maxPowerKw() * SLOT_HOURS * efficiency / kwhPerPercent);
        double cheapest = inputs.stream().mapToDouble(PlanInput::price).min().orElse(0);
        Setup setup = new Setup(low, states, start, kwhPerPercent, efficiency, changeCandidates(maxDown, maxUp), cheapest);

        // 계획은 방어선보다 조금 낮게 잡음 (여유): 주의선만큼(54 kW) → 20 kW → 0 순서로, 배터리가 버티는 가장 큰 여유
        double lineKw = limits.targetPeakKw();
        double usedMarginKw = 0;
        double[] essKw = null;
        for (double marginKw : new double[] {limits.targetPeakKw() - limits.warnKw(), 20, 0}) {
            essKw = solve(inputs, lineKw - marginKw, setup);
            usedMarginKw = marginKw;
            if (essKw != null) {
                break;
            }
        }
        // 피크 모드: 목표 피크를 못 지키면, 지킬 수 있는 가장 낮은 선을 5 kW 차이까지 반씩 좁혀 찾음
        if (essKw == null) {
            double canKeep = Math.ceil(inputs.stream().mapToDouble(PlanInput::peakNetKw).max().orElse(0));
            double cannotKeep = limits.targetPeakKw();
            while (canKeep - cannotKeep > 5) {
                double middle = Math.round((canKeep + cannotKeep) / 2);
                if (solve(inputs, middle, setup) != null) {
                    canKeep = middle;
                } else {
                    cannotKeep = middle;
                }
            }
            lineKw = canKeep;
            usedMarginKw = 0;
            essKw = solve(inputs, lineKw, setup);
            if (essKw == null) {
                essKw = new double[inputs.size()]; // ESS 없이도 지켜지는 선이라 여기 올 일은 없지만, 대비로 전부 대기
            }
        }
        return new PlanResult(lineKw, usedMarginKw, essKw);
    }

    // 방어선 lineKw를 지키는 가장 싼 계획. 지킬 수 없으면 null
    private double[] solve(List<PlanInput> inputs, double lineKw, Setup s) {
        int n = inputs.size();

        // 칸마다 그 칸부터 끝까지 남겨 둘 예비 잔량 (kWh). 뒤에서부터 더해 감
        double[] reserveKwh = new double[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            double peakNetKw = inputs.get(i).peakNetKw();
            double extraKw = Math.max(0, peakNetKw + RESERVE_EXTRA_KW - lineKw) - Math.max(0, peakNetKw - lineKw);
            reserveKwh[i] = reserveKwh[i + 1] + extraKw * SLOT_HOURS;
        }

        // after[잔량] = 다음 칸을 그 잔량으로 시작할 때 끝까지 드는 가장 싼 비용 (처음엔 끝 잔량의 값)
        double[] after = new double[s.states()];
        for (int state = 0; state < s.states(); state++) {
            after[state] = (END_SOC_PERCENT - (s.low() + state)) * s.kwhPerPercent() * s.cheapest() / s.efficiency();
        }
        int[][] picks = new int[n][s.states()];

        for (int i = n - 1; i >= 0; i--) {
            PlanInput input = inputs.get(i);
            double[] best = new double[s.states()];
            Arrays.fill(best, Double.POSITIVE_INFINITY);
            for (int state = 0; state < s.states(); state++) {
                // 이 칸을 시작할 때 잔량이 예비 잔량보다 모자라면 벌점
                double shortKwh = Math.max(0, reserveKwh[i] - state * s.kwhPerPercent());
                double reservePenalty = shortKwh * RESERVE_PENALTY_WON;
                for (int change : s.changes()) {
                    int next = state + change;
                    if (next < 0 || next >= s.states() || after[next] == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    double essKw = essKwOf(change, s);
                    // 방어선을 넘으면 안 됨
                    if (input.peakNetKw() - essKw > lineKw) {
                        continue;
                    }
                    // 방전한 전기가 남아서 한전 쪽으로 거꾸로 나가면(역송) 안 됨
                    double gridKw = input.netKw() - essKw;
                    if (essKw > 0 && gridKw < 0) {
                        continue;
                    }
                    // 충전·대기 중에 남는 태양광은 버려짐 (한전에 팔지 않음) → 산 전기는 0 아래로 내려가지 않음
                    double buyKw = Math.max(0, gridKw);
                    double wear = essKw > 0 ? essKw * SLOT_HOURS * WEAR_COST_WON_PER_KWH : 0;
                    double cost = buyKw * SLOT_HOURS * input.price() + wear + reservePenalty + after[next];
                    if (cost < best[state]) {
                        best[state] = cost;
                        picks[i][state] = change;
                    }
                }
            }
            after = best;
        }
        if (after[s.start()] == Double.POSITIVE_INFINITY) {
            return null;
        }

        // 지금 잔량에서 시작해 고른 길을 따라가며 칸마다 ESS 출력을 꺼냄
        double[] essKw = new double[n];
        int state = s.start();
        for (int i = 0; i < n; i++) {
            essKw[i] = essKwOf(picks[i][state], s);
            state += picks[i][state];
        }
        return essKw;
    }

    // 잔량 변화(%) → ESS 출력 kW. 충전은 손실이 있어 잔량보다 더 많이 사야 함
    private double essKwOf(int change, Setup s) {
        if (change < 0) {
            return -change * s.kwhPerPercent() / SLOT_HOURS;
        }
        return -(change * s.kwhPerPercent()) / s.efficiency() / SLOT_HOURS;
    }

    // 한 칸에서 잔량을 바꿔 볼 후보 (%). 비용이 같으면 앞쪽(덜 움직이는 쪽)을 고르게 0부터 놓음
    private List<Integer> changeCandidates(int maxDown, int maxUp) {
        List<Integer> list = new ArrayList<>();
        list.add(0);
        for (int step = 1; step <= Math.max(maxDown, maxUp); step++) {
            if (step <= maxDown) {
                list.add(-step);
            }
            if (step <= maxUp) {
                list.add(step);
            }
        }
        return list;
    }

    // ---------- 2) 지금 무엇을 추천할지 ----------

    // 계획을 따르되, 실제 전력이 계획과 달라 방어선을 넘을 것 같으면 이번 구간만 피크 방어를 추천
    // slotNetKw: 지금 칸의 한전 구매 예상 (음수면 남는 태양광 → 충전 이유를 잉여 충전으로)
    public Advice decide(PlanResult plan, NowInfo now, double slotNetKw, EssLimits limits) {
        double planKw = plan.essKw()[0]; // 계획의 지금 칸 출력
        double lineKw = plan.lineKw();
        double efficiency = limits.efficiencyPercent() / 100;
        double rest = Math.max(now.remainingMinutes(), 0.5);

        // 남은 구간 시간 동안 잔량으로 낼 수 있는 한계
        double restHours = rest / 60;
        double usableKwh = Math.max(0, (now.socPercent() - limits.socLowPercent()) / 100 * limits.capacityKwh());
        double emptyKwh = Math.max(0, (limits.socHighPercent() - now.socPercent()) / 100 * limits.capacityKwh());
        double dischargeLimitKw = Math.min(limits.maxPowerKw(), usableKwh / restHours);
        double chargeLimitKw = Math.min(limits.maxPowerKw(), emptyKwh / efficiency / restHours);

        // 계획대로(충전은 빼고) 해도 방어선을 넘으면, 넘는 만큼 더 방전
        double baseKw = Math.max(planKw, 0);
        double needKw = neededDischargeKw(now.predictedOf(baseKw), rest, lineKw, plan.marginKw());
        double wantKw = needKw > 0 ? baseKw + needKw : planKw;
        if (wantKw < 0) {
            // 충전 때문에 방어선 쪽으로 너무 올라가지 않게 충전을 줄임
            double aimKw = lineKw - plan.marginKw();
            double roomKw = (aimKw - now.predictedOf(0)) * 15 / rest;
            wantKw = -Math.min(-wantKw, Math.max(0, roomKw));
        }
        wantKw = Math.round(Math.max(-chargeLimitKw, Math.min(dischargeLimitKw, wantKw)));

        if (needKw >= MIN_ADVICE_KW) {
            return dischargeLimitKw >= MIN_ADVICE_KW
                    ? new Advice(RecommendationReasonType.PEAK_DEFENSE, RecommendationAction.DISCHARGE, wantKw)
                    : new Advice(RecommendationReasonType.SAFETY_LIMIT, RecommendationAction.IDLE, 0);
        }
        if (wantKw >= MIN_ADVICE_KW) {
            return new Advice(RecommendationReasonType.ON_PEAK_DISCHARGE, RecommendationAction.DISCHARGE, wantKw);
        }
        if (wantKw <= -MIN_ADVICE_KW) {
            RecommendationReasonType reason = slotNetKw < 0
                    ? RecommendationReasonType.SOLAR_SURPLUS_CHARGE
                    : RecommendationReasonType.OFF_PEAK_CHARGE;
            return new Advice(reason, RecommendationAction.CHARGE, wantKw);
        }
        return new Advice(RecommendationReasonType.IDLE, RecommendationAction.IDLE, 0);
    }

    // 이번 구간 평균이 방어선을 넘을 것 같을 때, 남은 시간 동안 더 방전해야 하는 kW
    // 15분 평균을 X kW 낮추려면 X × 15분만큼의 전력량을 빼야 하고, 그걸 남은 시간에 나눠 냄
    // 방어선보다 marginKw만큼 더 낮춤 (안전 여유)
    static double neededDischargeKw(double predictedKw, double remainingMinutes, double lineKw, double marginKw) {
        if (predictedKw <= lineKw) {
            return 0;
        }
        return (predictedKw - (lineKw - marginKw)) * 15 / Math.max(remainingMinutes, 0.5);
    }

    // ---------- 3) 이유 문장 ----------

    // 예측(높게 본 값)이 주의선을 넘는 칸을 피크 위험 칸으로 봄
    public PeakRisk peakRiskOf(List<PlanInput> inputs, EssLimits limits) {
        Integer first = null;
        double peakKw = 0;
        double needKwh = 0;
        for (int i = 0; i < inputs.size(); i++) {
            double overKw = inputs.get(i).peakNetKw() - limits.warnKw();
            if (overKw <= 0) {
                continue;
            }
            needKwh += overKw * SLOT_HOURS;
            if (first == null) {
                first = i;
            }
            peakKw = Math.max(peakKw, inputs.get(i).peakNetKw());
        }
        double needPercent = Math.min(limits.socHighPercent(),
                limits.socLowPercent() + needKwh / limits.capacityKwh() * 100);
        return new PeakRisk(first == null ? null : inputs.get(first).slotStart(), peakKw,
                first != null && first == 0, needPercent);
    }

    // 추천 카드에 보이는 이유 문장 (한두 문장)
    // asIsKw: 지금대로면 이번 15분 평균, afterKw: 추천대로 하면 이번 15분 평균
    // tariffLabel·price: 지금 요금 시간대와 단가, peakPrice: 최대부하 단가, solarSurplusKw: 남는 태양광
    public String explain(Advice advice, double asIsKw, double afterKw, double socPercent, PeakRisk risk,
                          String tariffLabel, double price, double peakPrice, double solarSurplusKw,
                          LocalDate today, EssLimits limits) {
        String target = kw(limits.targetPeakKw());
        String riskText = risk.startAt() == null ? null
                : timeText(risk.startAt(), today) + " 피크(예상 " + kw(risk.peakKw()) + ")";
        return switch (advice.reasonType()) {
            case PEAK_DEFENSE -> afterKw <= limits.targetPeakKw()
                    ? "적용하면 이번 15분 평균이 " + num(asIsKw) + " → " + kw(afterKw) + "로 목표(" + target
                            + ") 안에 들어옵니다. 넘기면 피크가 목표를 넘어 기본요금이 오를 수 있습니다."
                    : "적용하면 이번 15분 평균이 " + num(asIsKw) + " → " + kw(afterKw) + "로 목표(" + target
                            + ")에 가까워집니다.";
            case SAFETY_LIMIT -> "잔량이 하한(" + num(limits.socLowPercent()) + "%)이라 방전할 수 없어, 이번 15분 평균이 "
                    + kw(asIsKw) + "로 목표를 넘을 수 있습니다. 설비 가동 조정이 필요합니다.";
            case SOLAR_SURPLUS_CHARGE -> "적용하면 남는 태양광(약 " + kw(solarSurplusKw)
                    + ")을 버리지 않고 배터리에 담아 두었다가 비싼 시간(최대 " + won(peakPrice) + "원/kWh)에 씁니다.";
            case OFF_PEAK_CHARGE -> riskText != null && socPercent < risk.needPercent()
                    ? "적용하면 " + riskText + " 때 쓸 전기를 미리 채워 두어, 피크를 목표(" + target + ") 안으로 막을 수 있습니다."
                    : "적용하면 싼 " + tariffLabel + " 요금(" + won(price) + "원/kWh)으로 채워 두고, 비싼 시간(최대 "
                            + won(peakPrice) + "원/kWh)에 씁니다.";
            case ON_PEAK_DISCHARGE -> risk.nowInRisk()
                    ? "적용하면 피크 시간 한전 구매가 " + num(asIsKw) + " → " + kw(afterKw) + "로 목표(" + target + ") 안에 머뭅니다."
                    : "적용하면 비싼 " + tariffLabel + " 요금(" + won(price)
                            + "원/kWh) 대신 배터리 전기를 써서 전기요금을 줄입니다. 피크에 쓸 잔량은 남겨 둡니다.";
            case IDLE -> riskText != null
                    ? riskText + "에 대비해 잔량을 아껴 둡니다."
                    : "지금은 충·방전해도 이득이 없어 대기합니다.";
        };
    }

    // 1830.4 → "1,830 kW"
    private static String kw(double value) {
        return num(value) + " kW";
    }

    private static String num(double value) {
        return String.format("%,d", Math.round(value));
    }

    // 단가 123.0 → "123", 133.4 → "133.4"
    private static String won(double price) {
        return price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price);
    }

    // 오늘이면 "16:00", 내일이면 "내일 16:00"
    private static String timeText(LocalDateTime time, LocalDate today) {
        String hhmm = time.format(HH_MM);
        return time.toLocalDate().equals(today) ? hhmm : "내일 " + hhmm;
    }
}
