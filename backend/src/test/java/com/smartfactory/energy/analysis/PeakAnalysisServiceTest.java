package com.smartfactory.energy.analysis;

import static com.smartfactory.energy.TestData.dayTariffs;
import static com.smartfactory.energy.TestData.interval;
import static com.smartfactory.energy.TestData.recommendation;
import static com.smartfactory.energy.TestData.setting;
import static com.smartfactory.energy.TestData.tariff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.analysis.dto.PeakResponse;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffPeriodType;
import com.smartfactory.energy.tariff.TariffSeason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 피크 분석: 빈 기간, 진행 중 구간 제외, 목표 초과 수, 피크가 잦은 시간대 글자
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeakAnalysisServiceTest {

    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @Mock RecommendationRepository recommendationRepository;
    @Mock SettingRepository settingRepository;
    @Mock TariffPeriodRepository tariffPeriodRepository;
    @InjectMocks PeakAnalysisService peakAnalysisService;

    static final LocalDate DAY = LocalDate.of(2026, 11, 10); // 11월 = 겨울 요금

    @BeforeEach
    void setUp() {
        when(settingRepository.findById("peak.target_kw")).thenReturn(Optional.of(setting("peak.target_kw", "목표 피크", "1800")));
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(tariffPeriodRepository.findBySeason(TariffSeason.WINTER)).thenReturn(dayTariffs(TariffSeason.WINTER));
    }

    @Test
    void 시작이_끝보다_늦으면_400() {
        assertThatThrownBy(() -> peakAnalysisService.getPeak(DAY, DAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다_찬_구간이_없으면_hasData_false() {
        when(demandInterval15mRepository.findInPeriod(any(), any()))
                .thenReturn(List.of(interval(DAY.atTime(9, 0), 1500, 1500, 3)));

        PeakResponse peak = peakAnalysisService.getPeak(DAY, DAY);

        assertThat(peak.hasData()).isFalse();
        assertThat(peak.summary()).isNull();
        assertThat(peak.targetPeakKw()).isEqualTo(1800.0);
    }

    @Test
    void 진행_중_구간은_빼고_목표_초과_구간을_셈() {
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(DAY.atTime(9, 0), 1700, 1800, 15),
                interval(DAY.atTime(9, 15), 1850, 1950, 15),
                interval(DAY.atTime(9, 30), 2400, 2400, 5) // 진행 중
        ));

        PeakResponse peak = peakAnalysisService.getPeak(DAY, DAY);

        assertThat(peak.summary().maxPeakKw()).isEqualTo(1850.0);
        assertThat(peak.summary().exceededCount()).isEqualTo(1);
        assertThat(peak.summary().windowCount()).isEqualTo(2);
        assertThat(peak.dailyPeaks().get(0).expectedKw()).isEqualTo(1950.0);
        assertThat(peak.topWindows()).hasSize(1); // 하루에 1개
        assertThat(peak.topWindows().get(0).status()).isNull(); // 방전 추천 없음
    }

    @Test
    void 피크_구간의_처리_상태는_나중_추천보다_적용을_먼저_봄() {
        LocalDateTime w = DAY.atTime(9, 0);
        when(demandInterval15mRepository.findInPeriod(any(), any()))
                .thenReturn(List.of(interval(w, 1850, 1950, 15)));
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of(
                recommendation(1, w.plusMinutes(1), w, RecommendationAction.DISCHARGE,
                        RecommendationReasonType.PEAK_DEFENSE, RecommendationStatus.APPLIED, 1800),
                recommendation(2, w.plusMinutes(5), w, RecommendationAction.DISCHARGE,
                        RecommendationReasonType.PEAK_DEFENSE, RecommendationStatus.EXPIRED, 1800)
        ));

        PeakResponse peak = peakAnalysisService.getPeak(DAY, DAY);

        assertThat(peak.topWindows().get(0).status()).isEqualTo("APPLIED");
    }

    @Test
    void 피크가_잦은_시간대가_하루_끝까지면_끝_시각은_24시() {
        when(tariffPeriodRepository.findBySeason(TariffSeason.WINTER)).thenReturn(List.of(
                tariff(TariffSeason.WINTER, TariffPeriodType.OFF_PEAK, "00:00", "22:00", 100),
                tariff(TariffSeason.WINTER, TariffPeriodType.ON_PEAK, "22:00", "23:59:59", 200)
        ));
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(DAY.atTime(9, 0), 1500, 1500, 15),
                interval(DAY.atTime(22, 30), 1900, 1900, 15)
        ));

        PeakResponse peak = peakAnalysisService.getPeak(DAY, DAY);

        assertThat(peak.summary().busyFrom()).isEqualTo("22:00");
        assertThat(peak.summary().busyTo()).isEqualTo("24:00");
        assertThat(peak.summary().busyPeriodLabel()).isEqualTo("최대부하");
    }

    @Test
    void 요금표가_없으면_피크가_잦은_시간대는_null() {
        when(tariffPeriodRepository.findBySeason(TariffSeason.WINTER)).thenReturn(List.of());
        when(demandInterval15mRepository.findInPeriod(any(), any()))
                .thenReturn(List.of(interval(LocalDateTime.of(2026, 11, 10, 9, 0), 1500, 1500, 15)));

        PeakResponse peak = peakAnalysisService.getPeak(DAY, DAY);

        assertThat(peak.summary().busyFrom()).isNull();
        assertThat(peak.summary().busyTo()).isNull();
    }
}
