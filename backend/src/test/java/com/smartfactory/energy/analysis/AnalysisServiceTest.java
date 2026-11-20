package com.smartfactory.energy.analysis;

import static com.smartfactory.energy.TestData.bd;
import static com.smartfactory.energy.TestData.interval;
import static com.smartfactory.energy.TestData.recommendation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.analysis.dto.TrendResponse;
import com.smartfactory.energy.analysis.dto.TrendStatusResult;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.PowerSummaryTotals;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.setting.SettingCache;
import com.smartfactory.energy.tariff.TariffPeriodCache;
import com.smartfactory.energy.weather.WeatherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 추이 분석: 입력 검사, 빈 기간, 처리별 결과(같은 구간 중복·만료·진행 중 구간·반올림 경계)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisServiceTest {

    @Mock SitePowerMinuteRepository sitePowerMinuteRepository;
    @Mock EssStatusMinuteRepository essStatusMinuteRepository;
    @Mock WeatherRepository weatherRepository;
    @Mock SettingCache settingCache;
    @Mock TariffPeriodCache tariffPeriodCache;
    @Mock RecommendationRepository recommendationRepository;
    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @InjectMocks AnalysisService analysisService;

    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static final LocalDateTime W1 = DAY.atTime(14, 0);  // 다 찬 구간
    static final LocalDateTime W2 = DAY.atTime(14, 15); // 다 찬 구간
    static final LocalDateTime W3 = DAY.atTime(14, 30); // 진행 중 구간 (7분만 있음)

    @BeforeEach
    void setUp() {
        when(settingCache.getNumber(anyString())).thenReturn(null);
        when(settingCache.getNumber("peak.target_kw")).thenReturn(1800.0);
        when(tariffPeriodCache.findBySeason(any())).thenReturn(List.of());
        when(weatherRepository.findInPeriod(any(), any())).thenReturn(List.of());
        // 1분 기록이 하나도 없을 때의 기본값 (avgGridKw가 null이면 요약은 null)
        when(sitePowerMinuteRepository.summaryTotals(any(), any()))
                .thenReturn(new PowerSummaryTotals(null, null, null, null, null));
    }

    @Test
    void interval이_틀리면_400() {
        assertThatThrownBy(() -> analysisService.getTrend(DAY, DAY, "MINUTE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 시작이_끝보다_늦으면_400() {
        assertThatThrownBy(() -> analysisService.getTrend(DAY, DAY.minusDays(1), "HOUR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기간은_62일까지_되고_63일이면_400() {
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());

        analysisService.getTrend(DAY, DAY.plusDays(61), "DAY"); // 62일: 통과
        assertThatThrownBy(() -> analysisService.getTrend(DAY, DAY.plusDays(62), "DAY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기록이_없으면_요약은_null이고_목록은_비어_있음() {
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());

        TrendResponse trend = analysisService.getTrend(DAY, DAY, "HOUR");

        assertThat(trend.summary()).isNull();
        assertThat(trend.points()).isEmpty();
        assertThat(trend.hourly()).hasSize(24); // 시각표는 0~23시 칸이 항상 있음
        assertThat(trend.byStatus()).extracting(TrendStatusResult::count).containsExactly(0, 0, 0);
        assertThat(trend.targetPeakKw()).isEqualTo(1800.0);
    }

    @Test
    void 처리별_결과는_같은_구간을_한_번만_세고_만료는_넘김으로_셈() {
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(W1, 1790, 1900, 15),
                interval(W2, 1800.04, 1900, 15), // 반올림하면 1800.0이지만 목표 1800을 넘음
                interval(W3, 1500, 1500, 7)
        ));
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of(
                // W1: 1분마다 추천이 3줄 (만료 → 적용 → 대기) → 적용 1건으로 셈
                discharge(1, W1.plusMinutes(1), W1, RecommendationStatus.EXPIRED),
                discharge(2, W1.plusMinutes(2), W1, RecommendationStatus.APPLIED),
                discharge(3, W1.plusMinutes(3), W1, RecommendationStatus.PENDING),
                // W2: 만료만 있음 → 넘김 1건, 결과 1800.04 > 1800 이라 목표 달성 아님
                discharge(4, W2.plusMinutes(1), W2, RecommendationStatus.EXPIRED),
                // W3: 진행 중 구간 → 결과가 없어서 세지 않음
                discharge(5, W3.plusMinutes(1), W3, RecommendationStatus.AUTO)
        ));

        TrendResponse trend = analysisService.getTrend(DAY, DAY, "QUARTER");

        TrendStatusResult applied = trend.byStatus().get(0);
        TrendStatusResult auto = trend.byStatus().get(1);
        TrendStatusResult ignored = trend.byStatus().get(2);
        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.count()).isEqualTo(1);
        assertThat(applied.defendedCount()).isEqualTo(1);
        assertThat(auto.count()).isZero();
        assertThat(ignored.status()).isEqualTo("IGNORED");
        assertThat(ignored.count()).isEqualTo(1);
        assertThat(ignored.defendedCount()).isZero();
    }

    @Test
    void 최대_피크는_다_찬_구간에서만_고르고_목표_초과면_CRITICAL() {
        // summary()를 null이 아니게 만들기 위한 집계값 (powers(W1, 37, 1700)과 같은 뜻: 37분, 한전 1700kW)
        when(sitePowerMinuteRepository.summaryTotals(any(), any()))
                .thenReturn(new PowerSummaryTotals(1700.0, bd(1700.0 * 37), bd(0), bd(0), bd(0)));
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(W1, 1790, 1900, 15),
                interval(W2, 1800.04, 1900, 15),
                interval(W3, 2500, 2500, 7) // 진행 중 구간은 아무리 커도 피크가 아님
        ));

        TrendResponse trend = analysisService.getTrend(DAY, DAY, "HOUR");

        assertThat(trend.summary().maxPeakKw()).isEqualTo(1800.0);
        assertThat(trend.summary().maxPeakAt()).isEqualTo(W2);
        assertThat(trend.highlights().get(0).level()).isEqualTo("CRITICAL"); // 1800.04 > 1800
    }

    private static com.smartfactory.energy.recommendation.Recommendation discharge(
            long id, LocalDateTime at, LocalDateTime window, RecommendationStatus status) {
        return recommendation(id, at, window, RecommendationAction.DISCHARGE,
                RecommendationReasonType.PEAK_DEFENSE, status, 1800);
    }
}
