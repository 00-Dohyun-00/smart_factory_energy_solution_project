package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventLogRepository;
import com.smartfactory.energy.event.EventStage;
import com.smartfactory.energy.event.EventType;
import com.smartfactory.energy.loadforecast.LoadForecastService;
import com.smartfactory.energy.loadforecast.dto.LoadForecast;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.dto.EssPlanResponse;
import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import com.smartfactory.energy.setting.SettingCache;
import com.smartfactory.energy.simulator.EssSocState;
import com.smartfactory.energy.simulator.SimClock;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodCache;
import com.smartfactory.energy.tariff.TariffPeriodType;
import com.smartfactory.energy.tariff.TariffSeason;
import com.smartfactory.energy.weather.SolarForecastService;
import com.smartfactory.energy.weather.dto.SolarForecastSlot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판단 엔진. 1분마다(RecommendationEngineJob) 아래 순서로 지금 추천을 만듭니다.
 * 1. 계획에 넣을 값 모으기: 예상 부하(AI 서버), 예상 태양광(Open-Meteo), 요금, 지금 잔량, 환경 설정
 * 2. 지금부터 24시간(15분 96칸) ESS 계획 짜기 (EssPlanner.plan) → 칸마다 충·방전과 예상 잔량
 * 3. 이번 15분 구간의 실제 전력과 계획을 보고 지금 무엇을 추천할지 정하기 (EssPlanner.decide)
 * 4. 추천이 바뀌었거나 새 15분 구간이면 recommendation에 1줄 저장 (+ 이벤트, 자동 모드면 바로 적용)
 *
 * 부하 예측은 AI 서버(FastAPI)가 하고, 판단(충전·방전)은 여기서 최적화로 합니다. (11/18 결정)
 * AI 서버가 응답하지 않으면 최근 1시간 평균으로 대신 씁니다. (LoadForecastService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationEngine {

    // 계획 구간: 15분 96칸 = 24시간
    static final int HORIZON_SLOTS = 96;
    // 마지막 1분 기록이 이보다 오래됐으면 시뮬레이터가 멈춘 것으로 보고 판단하지 않음
    private static final int STALE_MINUTES = 2;
    // 부하 예측에 쓰는 최근 기록 길이 (분)
    private static final int LOAD_HISTORY_MINUTES = 60;
    // "최근 ESS 없이 한전 구매"를 볼 최근 분 수
    private static final int RECENT_MINUTES = 4;
    // 같은 구간에서 피크 방어 중일 때, 방전이 이만큼(kW) 이상 더 필요해지면 깜빡임 방지보다 우선해서 다시 추천
    private static final double ESCALATION_KW = 50;
    private static final String AUTO_HANDLER = "자동 모드";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final EssPlanner essPlanner;
    private final LoadForecastService loadForecastService;
    private final SolarForecastService solarForecastService;
    private final SimClock simClock;
    private final EssSocState essSocState;
    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final TariffPeriodCache tariffPeriodCache;
    private final SettingCache settingCache;
    private final RecommendationRepository recommendationRepository;
    private final EventLogRepository eventLogRepository;

    // 가장 최근에 짠 24시간 계획 (이 서버 메모리에만 있음)
    private volatile EssPlanResponse latestPlan;

    public Optional<EssPlanResponse> latestPlan() {
        return Optional.ofNullable(latestPlan);
    }

    public void run() {
        LocalDateTime now = simClock.now().withNano(0);

        // 1. 최근 1시간 1분 기록. 없거나 멈춰 있으면 판단하지 않음
        List<SitePowerMinute> history = sitePowerMinuteRepository.findInPeriod(
                now.minusMinutes(LOAD_HISTORY_MINUTES), now.plusMinutes(1));
        if (history.isEmpty() || history.get(history.size() - 1).getTs().isBefore(now.minusMinutes(STALE_MINUTES))) {
            return;
        }
        Map<String, String> settings = settingValues();
        EssLimits limits = limitsOf(settings);
        double socPercent = essSocState.get().doubleValue();
        LocalDateTime windowStart = quarterStartOf(now);
        // 요금표는 서버 시작 때 캐시된 걸 한 번만 읽어서 계획·저장 양쪽에 그대로 씀 (중복 조회 제거)
        List<TariffPeriod> periods = tariffPeriodCache.findAll();

        // 2. 24시간 계획 (부하는 AI 서버 예측, 안 되면 최근 1시간 평균)
        LoadForecast loadForecast = loadForecastService.forecast(windowStart, HORIZON_SLOTS, history);
        List<PlanInput> inputs = planInputsOf(windowStart, loadForecast, periods);
        EssPlanner.PlanResult plan = essPlanner.plan(inputs, socPercent, limits);
        latestPlan = planResponseOf(now, socPercent, plan, inputs, limits, loadForecast.method());

        // 3. 지금 추천
        EssPlanner.NowInfo info = nowInfoOf(history, windowStart, socPercent);
        EssPlanner.Advice advice = essPlanner.decide(plan, info, inputs.get(0).netKw(), limits);

        // 4. 저장
        saveIfChanged(now, windowStart, advice, info, inputs, limits, settings, periods);
    }

    // ---------- 계획에 넣을 값 ----------

    // 지금 15분 칸부터 96칸: 예상 부하 − 예상 태양광, 단가
    List<PlanInput> planInputsOf(LocalDateTime windowStart, LoadForecast loadForecast, List<TariffPeriod> periods) {
        List<SolarForecastSlot> solar = solarForecastService.forecastFrom(windowStart, HORIZON_SLOTS);

        List<PlanInput> inputs = new ArrayList<>();
        for (int i = 0; i < HORIZON_SLOTS; i++) {
            LocalDateTime slotStart = windowStart.plusMinutes(15L * i);
            TariffPeriod period = periodAt(periods, slotStart.plusSeconds(450)); // 칸 가운데 시각의 요금
            LoadForecast.Slot load = loadForecast.slots().get(i);
            inputs.add(new PlanInput(
                    slotStart,
                    load.loadKw(),
                    load.peakLoadKw(),
                    solar.get(i).solarKw(),
                    period != null ? period.getUnitPrice().doubleValue() : 0,
                    period != null ? period.getPeriodType() : null
            ));
        }
        return inputs;
    }

    // 이번 15분 구간 상황 (지금 상태 API의 예상 피크와 같은 방식: 남은 분 = 15 − 쌓인 1분 기록 수)
    private EssPlanner.NowInfo nowInfoOf(List<SitePowerMinute> history, LocalDateTime windowStart, double socPercent) {
        List<SitePowerMinute> windowRows = history.stream().filter(r -> !r.getTs().isBefore(windowStart)).toList();
        double gridSum = windowRows.stream().mapToDouble(r -> r.getGridKw().doubleValue()).sum();
        int remainingMinutes = Math.max(0, 15 - windowRows.size());

        // 최근 몇 분 동안 ESS 없이 한전에서 샀을 전력 = 부하 − 태양광
        List<SitePowerMinute> recent = history.subList(Math.max(0, history.size() - RECENT_MINUTES), history.size());
        double recentWithoutEss = recent.stream()
                .mapToDouble(r -> r.getLoadKw().doubleValue() - r.getSolarKw().doubleValue())
                .average().orElse(0);
        return new EssPlanner.NowInfo(gridSum / 15, recentWithoutEss, remainingMinutes, socPercent);
    }

    // ---------- 저장 ----------

    // 새 15분 구간이거나 추천이 바뀌었을 때만 1줄 저장
    // 같은 구간에서 피크 방어가 나왔으면 그 구간이 끝날 때까지 유지 (방전 → 대기 → 방전 깜빡임 방지)
    // 단, 피크 방어 중에 더 많이 방전해야 하는 상황으로 악화되면(ESCALATION_KW 이상 더 필요) 그 유지
    // 규칙보다 우선해서 다시 추천함 (예: 40kW 적용해 뒀는데 갑자기 500kW가 필요해진 경우)
    private void saveIfChanged(LocalDateTime now, LocalDateTime windowStart, EssPlanner.Advice advice,
                               EssPlanner.NowInfo info, List<PlanInput> inputs, EssLimits limits,
                               Map<String, String> settings, List<TariffPeriod> periods) {
        Optional<Recommendation> latest = recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc();
        if (latest.isPresent()) {
            Recommendation last = latest.get();
            boolean sameWindow = last.getIntervalStart().equals(windowStart);
            boolean sameAdvice = last.getReasonType() == advice.reasonType() && last.getAction() == advice.action();
            boolean needsMoreDefense = advice.reasonType() == RecommendationReasonType.PEAK_DEFENSE
                    && last.getReasonType() == RecommendationReasonType.PEAK_DEFENSE
                    && Math.abs(advice.wantKw()) - last.getPowerKw().doubleValue() >= ESCALATION_KW;
            if (sameWindow && !needsMoreDefense && (sameAdvice || last.getReasonType() == RecommendationReasonType.PEAK_DEFENSE)) {
                return;
            }
            // 대기 추천은 구간이 바뀌어도 대기가 이어지면 새로 쌓지 않음 (이력이 대기로 가득 차지 않게)
            if (!sameWindow && sameAdvice && advice.action() == RecommendationAction.IDLE) {
                return;
            }
            // 답하지 않은 이전 추천은 만료
            if (last.getStatus() == RecommendationStatus.PENDING) {
                last.handle(RecommendationStatus.EXPIRED, null, now);
            }
        }

        double powerKw = Math.abs(advice.wantKw());
        double sign = advice.action() == RecommendationAction.DISCHARGE ? 1
                : advice.action() == RecommendationAction.CHARGE ? -1 : 0;
        double asIsKw = info.predictedOf(currentEssKw());
        double afterKw = info.predictedOf(sign * powerKw);
        TariffPeriod period = periodAt(periods, now);
        String description = essPlanner.explain(
                advice, asIsKw, afterKw, info.socPercent(), essPlanner.peakRiskOf(inputs, limits),
                period != null ? periodLabel(period.getPeriodType()) : "",
                period != null ? period.getUnitPrice().doubleValue() : 0,
                onPeakPrice(now.toLocalDate(), periods),
                Math.max(0, -inputs.get(0).netKw()),
                now.toLocalDate(), limits);

        boolean auto = "true".equalsIgnoreCase(settings.get("operation.auto_mode"));
        Recommendation recommendation = recommendationRepository.save(new Recommendation(
                now, windowStart, advice.action(), decimal(powerKw), advice.reasonType(), description,
                decimal(info.predictedOf(0)), decimal(limits.targetPeakKw()),
                auto ? RecommendationStatus.AUTO : RecommendationStatus.PENDING,
                auto ? AUTO_HANDLER : null, auto ? now : null));

        if (advice.action() != RecommendationAction.IDLE) {
            eventLogRepository.save(new EventLog(now, EventType.RECOMMEND, stageOf(advice),
                    recommendMessage(recommendation, windowStart), recommendation.getRecommendationId()));
        }
        // 자동 모드면 사람이 누르지 않아도 바로 적용(AUTO) 상태로 저장되어 있어서(위), 시뮬레이터가
        // currentCommand()로 DB를 보고 바로 따름. 대기(IDLE)는 따로 할 일이 없음
        if (auto && advice.action() != RecommendationAction.IDLE) {
            eventLogRepository.save(new EventLog(now, EventType.HANDLE, null,
                    actionText(advice.action()) + " " + num(powerKw) + " kW 자동 적용 (시뮬레이션)",
                    recommendation.getRecommendationId()));
        }
    }

    // 예: "방어선 초과 예상, 방전 176 kW 추천", "저요금 충전 250 kW 추천 (16:15까지)"
    private String recommendMessage(Recommendation r, LocalDateTime windowStart) {
        String power = num(r.getPowerKw().doubleValue()) + " kW";
        if (r.getReasonType() == RecommendationReasonType.PEAK_DEFENSE) {
            return "방어선 초과 예상, 방전 " + power + " 추천";
        }
        String label = RecommendationResponse.from(r, null).ruleLabel();
        return label + " " + power + " 추천 (" + windowStart.plusMinutes(15).format(HH_MM) + "까지)";
    }

    private EventStage stageOf(EssPlanner.Advice advice) {
        return advice.reasonType() == RecommendationReasonType.PEAK_DEFENSE
                || advice.reasonType() == RecommendationReasonType.SAFETY_LIMIT ? EventStage.WARNING : null;
    }

    // 계획 + 칸마다 예상 잔량 (화면·확인용)
    private EssPlanResponse planResponseOf(LocalDateTime now, double socPercent, EssPlanner.PlanResult plan,
                                           List<PlanInput> inputs, EssLimits limits, String loadForecastMethod) {
        List<EssPlanResponse.Slot> slots = new ArrayList<>();
        double soc = socPercent;
        for (int i = 0; i < inputs.size(); i++) {
            PlanInput input = inputs.get(i);
            double essKw = plan.essKw()[i];
            soc = Math.max(limits.socLowPercent(), Math.min(limits.socHighPercent(), soc + limits.socChangeOf(essKw)));
            slots.add(new EssPlanResponse.Slot(
                    input.slotStart(),
                    round1(input.loadKw()),
                    round1(input.solarKw()),
                    round1(input.netKw()),
                    input.price(),
                    input.periodType() != null ? input.periodType().name() : null,
                    round1(essKw),
                    round1(Math.max(0, input.netKw() - essKw)),
                    round1(soc)
            ));
        }
        return new EssPlanResponse(now, round1(socPercent), plan.lineKw(), plan.marginKw(), loadForecastMethod, slots);
    }

    // ---------- 작은 도우미 ----------

    // 지금 ESS 출력 (가장 최근 1분 기록)
    private double currentEssKw() {
        return sitePowerMinuteRepository.findFirstByOrderByTsDesc()
                .map(r -> r.getEssKw().doubleValue())
                .orElse(0.0);
    }

    // setting 전체를 키 → 값으로 (메모리 캐시, DB는 안 읽음)
    private Map<String, String> settingValues() {
        return settingCache.values();
    }

    // 환경 설정 값. 없으면 설정 화면 기본값
    static EssLimits limitsOf(Map<String, String> values) {
        double targetPeakKw = number(values, "peak.target_kw", 1800);
        double warnRatio = number(values, "peak.warn_ratio", 97);
        return new EssLimits(
                targetPeakKw,
                targetPeakKw * warnRatio / 100,
                number(values, "ess.max_power_kw", 500),
                number(values, "ess.capacity_kwh", 1000),
                number(values, "ess.soc_low", 20),
                number(values, "ess.soc_high", 90),
                number(values, "ess.efficiency_percent", 89.5)
        );
    }

    private static double number(Map<String, String> values, String key, double defaultValue) {
        try {
            return values.containsKey(key) ? Double.parseDouble(values.get(key)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // 그 시각이 속한 요금 시간대 (계절은 그 날짜 기준). 없으면 null
    static TariffPeriod periodAt(List<TariffPeriod> periods, LocalDateTime time) {
        TariffSeason season = seasonOf(time.toLocalDate());
        LocalTime clock = time.toLocalTime();
        for (TariffPeriod p : periods) {
            if (p.getSeason() != season) {
                continue;
            }
            LocalTime end = p.getEndTime();
            boolean endsAtDayEnd = end.equals(LocalTime.MIDNIGHT) || !end.isBefore(LocalTime.of(23, 59));
            if (!clock.isBefore(p.getStartTime()) && (endsAtDayEnd || clock.isBefore(end))) {
                return p;
            }
        }
        return null;
    }

    // 그 날짜 계절의 최대부하 단가
    private double onPeakPrice(LocalDate date, List<TariffPeriod> periods) {
        TariffSeason season = seasonOf(date);
        return periods.stream()
                .filter(p -> p.getSeason() == season && p.getPeriodType() == TariffPeriodType.ON_PEAK)
                .mapToDouble(p -> p.getUnitPrice().doubleValue())
                .max()
                .orElse(0);
    }

    // 한전 요금 계절: 6~8월 여름, 3~5월·9~10월 봄가을, 나머지 겨울
    private static TariffSeason seasonOf(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 6 && month <= 8) {
            return TariffSeason.SUMMER;
        }
        if (month >= 3 && month <= 10) {
            return TariffSeason.SPRING_FALL;
        }
        return TariffSeason.WINTER;
    }

    private static String periodLabel(TariffPeriodType type) {
        return switch (type) {
            case OFF_PEAK -> "경부하";
            case MID_PEAK -> "중간부하";
            case ON_PEAK -> "최대부하";
        };
    }

    private static String actionText(RecommendationAction action) {
        return switch (action) {
            case CHARGE -> "충전";
            case DISCHARGE -> "방전";
            case IDLE -> "대기";
        };
    }

    // 시각이 들어 있는 15분 칸의 시작 (예: 14:51 → 14:45)
    static LocalDateTime quarterStartOf(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.HOURS).plusMinutes(time.getMinute() / 15 * 15);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String num(double value) {
        return String.format("%,d", Math.round(value));
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
