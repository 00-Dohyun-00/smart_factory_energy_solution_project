package com.smartfactory.energy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Test;

// 24시간 계획(요금 차익, 피크 방어, 못 막는 날, 남는 태양광)과 지금 추천 판단, 이유 문장
class EssPlannerTest {

    // 목표 1,800 / 주의선 1,746 / 500 kW / 1,000 kWh / 잔량 20~90% / 효율 89.5%
    private static final EssLimits LIMITS = new EssLimits(1800, 1746, 500, 1000, 20, 90, 89.5);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 19, 0, 0);

    private final EssPlanner planner = new EssPlanner();

    // 96칸 입력. load·solar·price는 칸 번호로 정함
    private static List<PlanInput> inputs(IntToDoubleFunction load, IntToDoubleFunction solar, IntToDoubleFunction price) {
        List<PlanInput> list = new ArrayList<>();
        for (int i = 0; i < 96; i++) {
            double kw = load.applyAsDouble(i);
            list.add(new PlanInput(START.plusMinutes(15L * i), kw, kw, solar.applyAsDouble(i), price.applyAsDouble(i), null));
        }
        return list;
    }

    // 계획대로 했을 때 칸마다 잔량
    private static double[] socPath(double startSoc, double[] essKw) {
        double[] soc = new double[essKw.length];
        double value = startSoc;
        for (int i = 0; i < essKw.length; i++) {
            value += LIMITS.socChangeOf(essKw[i]);
            soc[i] = value;
        }
        return soc;
    }

    @Test
    void 싼_시간에_충전하고_비싼_시간에_방전함() {
        // 0~8시 100원, 8~16시 200원, 16~24시 100원. 부하 300 kW, 태양광 없음
        List<PlanInput> in = inputs(i -> 300, i -> 0, i -> i >= 32 && i < 64 ? 200 : 100);

        EssPlanner.PlanResult plan = planner.plan(in, 50, LIMITS);

        double[] ess = plan.essKw();
        double cheapCharge = 0;
        double expensiveDischarge = 0;
        for (int i = 0; i < 96; i++) {
            if (i < 32) {
                assertThat(ess[i]).isLessThanOrEqualTo(0); // 싼 아침엔 방전 안 함
                cheapCharge += -ess[i];
            } else if (i < 64) {
                assertThat(ess[i]).isGreaterThanOrEqualTo(0); // 비싼 낮엔 충전 안 함
                assertThat(ess[i]).isLessThanOrEqualTo(300); // 부하보다 많이 방전하면 역송이라 안 됨
                expensiveDischarge += ess[i];
            }
        }
        assertThat(cheapCharge).isPositive();
        assertThat(expensiveDischarge).isPositive();
        for (double soc : socPath(50, ess)) {
            assertThat(soc).isBetween(19.99, 90.01);
        }
    }

    @Test
    void 목표를_넘는_칸은_주의선_아래로_방전해서_막음() {
        // 10:00~11:00(40~43칸)만 2,000 kW, 나머지 1,700 kW
        List<PlanInput> in = inputs(i -> i >= 40 && i < 44 ? 2000 : 1700, i -> 0, i -> 150);

        EssPlanner.PlanResult plan = planner.plan(in, 50, LIMITS);

        assertThat(plan.lineKw()).isEqualTo(1800);
        assertThat(plan.marginKw()).isEqualTo(54); // 주의선만큼 여유를 둘 수 있는 날
        for (int i = 40; i < 44; i++) {
            assertThat(2000 - plan.essKw()[i]).isLessThanOrEqualTo(1746 + 1e-6);
        }
    }

    @Test
    void 배터리로_다_못_막는_날은_막을_수_있는_가장_낮은_선으로_올림() {
        // 하루 종일 2,500 kW: 최대 출력 500 kW로도 1,800은 못 지킴
        List<PlanInput> in = inputs(i -> 2500, i -> 0, i -> 150);

        EssPlanner.PlanResult plan = planner.plan(in, 50, LIMITS);

        assertThat(plan.lineKw()).isGreaterThan(1800).isLessThanOrEqualTo(2500);
        assertThat(plan.marginKw()).isEqualTo(0);
    }

    @Test
    void 남는_태양광이_있어도_계획을_짜고_그때는_충전만_함() {
        // 부하 300 kW, 2:30~7:30(10~29칸) 태양광 600 kW → 300 kW가 남음
        List<PlanInput> in = inputs(i -> 300, i -> i >= 10 && i < 30 ? 600 : 0, i -> 150);

        EssPlanner.PlanResult plan = planner.plan(in, 30, LIMITS);

        double surplusCharge = 0;
        for (int i = 10; i < 30; i++) {
            assertThat(plan.essKw()[i]).isLessThanOrEqualTo(0); // 남는 전기가 있을 때 방전하면 역송
            surplusCharge += -plan.essKw()[i];
        }
        assertThat(surplusCharge).isPositive(); // 버려질 전기를 담아 둠
    }

    @Test
    void 이번_구간이_방어선을_넘을_것_같으면_피크_방어() {
        EssPlanner.PlanResult plan = new EssPlanner.PlanResult(1800, 54, new double[96]);
        // 10분 동안 1,900 kW, 앞으로도 1,900 kW, 5분 남음 → 예상 1,900
        EssPlanner.NowInfo now = new EssPlanner.NowInfo(1900.0 * 10 / 15, 1900, 5, 60);

        EssPlanner.Advice advice = planner.decide(plan, now, 1900, LIMITS);

        assertThat(advice.reasonType()).isEqualTo(RecommendationReasonType.PEAK_DEFENSE);
        assertThat(advice.action()).isEqualTo(RecommendationAction.DISCHARGE);
        // (1,900 − 1,746) × 15 ÷ 5 = 462
        assertThat(advice.wantKw()).isEqualTo(462);
    }

    @Test
    void 잔량이_하한이면_안전_제한() {
        EssPlanner.PlanResult plan = new EssPlanner.PlanResult(1800, 54, new double[96]);
        EssPlanner.NowInfo now = new EssPlanner.NowInfo(1900.0 * 10 / 15, 1900, 5, 20);

        EssPlanner.Advice advice = planner.decide(plan, now, 1900, LIMITS);

        assertThat(advice.reasonType()).isEqualTo(RecommendationReasonType.SAFETY_LIMIT);
        assertThat(advice.action()).isEqualTo(RecommendationAction.IDLE);
    }

    @Test
    void 남는_태양광으로_충전하는_계획이면_잉여_충전() {
        double[] ess = new double[96];
        ess[0] = -200;
        EssPlanner.PlanResult plan = new EssPlanner.PlanResult(1800, 54, ess);
        EssPlanner.NowInfo now = new EssPlanner.NowInfo(0, -300, 15, 50);

        EssPlanner.Advice advice = planner.decide(plan, now, -300, LIMITS);

        assertThat(advice.reasonType()).isEqualTo(RecommendationReasonType.SOLAR_SURPLUS_CHARGE);
        assertThat(advice.action()).isEqualTo(RecommendationAction.CHARGE);
        assertThat(advice.wantKw()).isEqualTo(-200);
    }

    @Test
    void 계획이_대기면_조치_없음() {
        EssPlanner.PlanResult plan = new EssPlanner.PlanResult(1800, 54, new double[96]);
        EssPlanner.NowInfo now = new EssPlanner.NowInfo(100, 300, 10, 50);

        EssPlanner.Advice advice = planner.decide(plan, now, 300, LIMITS);

        assertThat(advice.reasonType()).isEqualTo(RecommendationReasonType.IDLE);
        assertThat(advice.wantKw()).isEqualTo(0);
    }

    @Test
    void 필요한_추가_방전량() {
        // 방어선 1,800, 예상 1,830, 10분 남음, 여유 54 → (1,830 − 1,746) × 15 ÷ 10 = 126
        assertThat(EssPlanner.neededDischargeKw(1830, 10, 1800, 54)).isCloseTo(126, within(1e-9));
        assertThat(EssPlanner.neededDischargeKw(1700, 10, 1800, 54)).isEqualTo(0);
    }

    @Test
    void 피크_방어_이유_문장() {
        EssPlanner.Advice advice = new EssPlanner.Advice(RecommendationReasonType.PEAK_DEFENSE, RecommendationAction.DISCHARGE, 462);
        EssPlanner.PeakRisk risk = new EssPlanner.PeakRisk(null, 0, false, 20);

        String text = planner.explain(advice, 1900, 1746, 60, risk, "최대부하", 204.6, 204.6, 0,
                LocalDate.of(2026, 11, 19), LIMITS);

        assertThat(text).isEqualTo("적용하면 이번 15분 평균이 1,900 → 1,746 kW로 목표(1,800 kW) 안에 들어옵니다."
                + " 넘기면 피크가 목표를 넘어 기본요금이 오를 수 있습니다.");
    }

    @Test
    void 피크_위험은_주의선을_넘는_첫_칸과_필요한_잔량() {
        // 40~43칸만 1,846 kW (주의선보다 100 kW 높음) → 100 × 0.25 × 4 = 100 kWh = 10% + 하한 20% = 30%
        List<PlanInput> in = inputs(i -> i >= 40 && i < 44 ? 1846 : 1000, i -> 0, i -> 150);

        EssPlanner.PeakRisk risk = planner.peakRiskOf(in, LIMITS);

        assertThat(risk.startAt()).isEqualTo(START.plusMinutes(15L * 40));
        assertThat(risk.peakKw()).isEqualTo(1846);
        assertThat(risk.nowInRisk()).isFalse();
        assertThat(risk.needPercent()).isCloseTo(30, within(1e-9));
    }
}
