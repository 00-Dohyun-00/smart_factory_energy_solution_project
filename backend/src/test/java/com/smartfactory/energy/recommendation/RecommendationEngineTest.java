package com.smartfactory.energy.recommendation;

import static com.smartfactory.energy.TestData.bd;
import static com.smartfactory.energy.TestData.dayTariffs;
import static com.smartfactory.energy.TestData.power;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventLogRepository;
import com.smartfactory.energy.event.EventType;
import com.smartfactory.energy.loadforecast.LoadForecastService;
import com.smartfactory.energy.loadforecast.dto.LoadForecast;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.dto.EssPlanResponse;
import com.smartfactory.energy.setting.SettingCache;
import com.smartfactory.energy.simulator.EssSocState;
import com.smartfactory.energy.simulator.SimClock;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodCache;
import com.smartfactory.energy.tariff.TariffSeason;
import com.smartfactory.energy.weather.SolarForecastService;
import com.smartfactory.energy.weather.dto.SolarForecastSlot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 판단 엔진: 계획(96칸·잔량), 추천 저장, 같은 구간·같은 추천이면 다시 저장하지 않음, 자동 모드, 멈춘 기록
// "지금"은 SimClock에서 얻으므로, 테스트에서는 항상 아래 NOW(심야 경부하 시간, 구간 6분째)로 고정해서
// 돌릴 때마다 같은 결과가 나오게 함. 최대부하 시간대에 돌리면 계획 자체가 미리 방전하려고 해서
// "피크 방어(PEAK_DEFENSE)"와 "최대부하 할인 방전(ON_PEAK_DISCHARGE)"이 구분되지 않기 때문
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationEngineTest {

    // 2026-01-15(겨울) 02:05 = 경부하 시간대(00:00~08:00), 15분 구간(02:00~02:15)의 6분째(남은 9분)
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 2, 5, 0);

    @Spy EssPlanner essPlanner = new EssPlanner();
    @Mock LoadForecastService loadForecastService;
    @Mock SolarForecastService solarForecastService;
    @Mock SimClock simClock;
    @Mock EssSocState essSocState;
    @Mock SitePowerMinuteRepository sitePowerMinuteRepository;
    @Mock TariffPeriodCache tariffPeriodCache;
    @Mock SettingCache settingCache;
    @Mock RecommendationRepository recommendationRepository;
    @Mock EventLogRepository eventLogRepository;
    @InjectMocks RecommendationEngine recommendationEngine;

    @BeforeEach
    void setUp() {
        when(simClock.now()).thenReturn(NOW);
        List<TariffPeriod> all = new ArrayList<>();
        for (TariffSeason season : TariffSeason.values()) {
            all.addAll(dayTariffs(season));
            when(tariffPeriodCache.findBySeason(season)).thenReturn(dayTariffs(season));
        }
        when(tariffPeriodCache.findAll()).thenReturn(all);
        when(settingCache.values()).thenReturn(Map.of());
        when(essSocState.get()).thenReturn(bd(50));
        when(solarForecastService.forecastFrom(any(), anyInt())).thenAnswer(inv -> {
            LocalDateTime from = inv.getArgument(0);
            List<SolarForecastSlot> slots = new ArrayList<>();
            for (int i = 0; i < (int) inv.getArgument(1); i++) {
                slots.add(new SolarForecastSlot(from.plusMinutes(15L * i), 0.0, 0, null, null));
            }
            return slots;
        });
        // 부하 예측: 최근 기록 평균을 모든 칸에 (AI 서버 없이 대신 쓰는 예측과 같은 모양)
        when(loadForecastService.forecast(any(), anyInt(), any())).thenAnswer(inv -> {
            LocalDateTime from = inv.getArgument(0);
            List<SitePowerMinute> rows = inv.getArgument(2);
            double mean = rows.stream().mapToDouble(r -> r.getLoadKw().doubleValue()).average().orElse(0);
            List<LoadForecast.Slot> slots = new ArrayList<>();
            for (int i = 0; i < (int) inv.getArgument(1); i++) {
                slots.add(new LoadForecast.Slot(from.plusMinutes(15L * i), mean, mean));
            }
            return new LoadForecast("테스트 예측", false, 0, slots);
        });
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc()).thenReturn(Optional.empty());
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // 지금까지 1시간 동안 1분 기록 (부하 = 한전 구매 = loadKw, 태양광·ESS 0)
    private void history(double loadKw, int minutesAgoOfLast) {
        history(loadKw, loadKw, minutesAgoOfLast);
    }

    // 평소에는 usualKw, 이번 15분 구간과 최근 4분은 nowKw
    private void history(double usualKw, double nowKw, int minutesAgoOfLast) {
        LocalDateTime last = NOW.minusMinutes(minutesAgoOfLast);
        LocalDateTime windowStart = RecommendationEngine.quarterStartOf(NOW);
        List<SitePowerMinute> rows = new ArrayList<>();
        for (int i = 59; i >= 0; i--) {
            LocalDateTime ts = last.minusMinutes(i);
            double kw = !ts.isBefore(windowStart) || i < 4 ? nowKw : usualKw;
            rows.add(power(ts, kw, 0, 0, kw));
        }
        when(sitePowerMinuteRepository.findInPeriod(any(), any())).thenReturn(rows);
        when(sitePowerMinuteRepository.findFirstByOrderByTsDesc()).thenReturn(Optional.of(rows.get(rows.size() - 1)));
    }

    private Recommendation savedRecommendation() {
        ArgumentCaptor<Recommendation> saved = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void 이십사시간_계획과_예상_잔량을_만들고_추천을_저장함() {
        history(300, 0);

        recommendationEngine.run();

        EssPlanResponse plan = recommendationEngine.latestPlan().orElseThrow();
        assertThat(plan.slots()).hasSize(96);
        assertThat(plan.slots().get(0).slotStart()).isEqualTo(RecommendationEngine.quarterStartOf(NOW));
        assertThat(plan.slots().get(95).slotStart()).isEqualTo(plan.slots().get(0).slotStart().plusMinutes(15 * 95));
        assertThat(plan.slots()).allSatisfy(slot -> {
            assertThat(slot.loadKw()).isEqualTo(300);
            assertThat(slot.socPercent()).isBetween(20.0, 90.0);
        });

        Recommendation r = savedRecommendation();
        assertThat(r.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(r.getIntervalStart()).isEqualTo(plan.slots().get(0).slotStart());
        assertThat(r.getDescription()).isNotBlank();
    }

    @Test
    void 이번_구간이_목표를_넘을_것_같으면_피크_방어_추천과_이벤트() {
        history(300, 2000, 0);

        recommendationEngine.run();

        Recommendation r = savedRecommendation();
        assertThat(r.getReasonType()).isEqualTo(RecommendationReasonType.PEAK_DEFENSE);
        assertThat(r.getAction()).isEqualTo(RecommendationAction.DISCHARGE);
        assertThat(r.getPredictedAvgKw()).isEqualByComparingTo("2000");
        ArgumentCaptor<EventLog> event = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(EventType.RECOMMEND);
        assertThat(event.getValue().getMessage()).startsWith("방어선 초과 예상, 방전");
    }

    @Test
    void 같은_구간에_같은_추천이면_다시_저장하지_않음() {
        history(300, 2000, 0);
        recommendationEngine.run();
        Recommendation first = savedRecommendation();
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc()).thenReturn(Optional.of(first));

        recommendationEngine.run();

        verify(recommendationRepository, times(1)).save(any());
    }

    @Test
    void 피크_방어_중_훨씬_더_방전해야_하면_유지_규칙보다_우선해서_다시_추천함() {
        history(300, 5000, 0); // 아주 큰 스파이크 → 최대 출력(500kW)까지 필요
        LocalDateTime windowStart = RecommendationEngine.quarterStartOf(NOW);
        // 같은 구간에 이미 40kW 방전이 적용돼 있음 (원래 규칙대로면 구간 끝까지 유지됨)
        Recommendation last = new Recommendation(windowStart.plusMinutes(1), windowStart, RecommendationAction.DISCHARGE,
                bd(40), RecommendationReasonType.PEAK_DEFENSE, "이전 추천", bd(1900), bd(1800),
                RecommendationStatus.APPLIED, "운영자", windowStart.plusMinutes(1));
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc())
                .thenReturn(Optional.of(last));

        recommendationEngine.run();

        Recommendation r = savedRecommendation();
        assertThat(r.getReasonType()).isEqualTo(RecommendationReasonType.PEAK_DEFENSE);
        assertThat(r.getAction()).isEqualTo(RecommendationAction.DISCHARGE);
        // 40 + ESCALATION_KW(50) 이상 커야 다시 추천되므로, 그보다 확실히 큰지 확인
        assertThat(r.getPowerKw().doubleValue()).isGreaterThanOrEqualTo(40 + 50);
    }

    @Test
    void 피크_방어_중이어도_차이가_작으면_유지_규칙대로_다시_저장하지_않음() {
        history(300, 2000, 0);
        recommendationEngine.run();
        Recommendation first = savedRecommendation();
        // 직전과 똑같은 상황을 다시 돌림 → 차이 0 < ESCALATION_KW(50) → 유지 규칙대로 그대로 둠
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc())
                .thenReturn(Optional.of(first));

        recommendationEngine.run();

        verify(recommendationRepository, times(1)).save(any());
    }

    @Test
    void 새_구간이면_답하지_않은_이전_추천은_만료() {
        history(300, 2000, 0);
        LocalDateTime previousWindow = RecommendationEngine.quarterStartOf(NOW).minusMinutes(15);
        Recommendation old = new Recommendation(previousWindow, previousWindow, RecommendationAction.DISCHARGE, bd(100),
                RecommendationReasonType.PEAK_DEFENSE, "이전", bd(1900), bd(1800), RecommendationStatus.PENDING, null, null);
        when(recommendationRepository.findFirstByOrderByRecommendedAtDescRecommendationIdDesc()).thenReturn(Optional.of(old));

        recommendationEngine.run();

        assertThat(old.getStatus()).isEqualTo(RecommendationStatus.EXPIRED);
        verify(recommendationRepository, times(1)).save(any());
    }

    @Test
    void 자동_모드면_바로_적용함() {
        history(300, 2000, 0);
        when(settingCache.values()).thenReturn(Map.of("operation.auto_mode", "true"));

        recommendationEngine.run();

        Recommendation r = savedRecommendation();
        assertThat(r.getStatus()).isEqualTo(RecommendationStatus.AUTO);
        assertThat(r.getHandledBy()).isEqualTo("자동 모드");
        verify(eventLogRepository, times(2)).save(any()); // 추천 + 자동 적용
    }

    @Test
    void 기록이_멈춰_있으면_판단하지_않음() {
        history(2000, 5); // 마지막 기록이 5분 전

        recommendationEngine.run();

        verify(recommendationRepository, never()).save(any());
        assertThat(recommendationEngine.latestPlan()).isEmpty();
    }
}
