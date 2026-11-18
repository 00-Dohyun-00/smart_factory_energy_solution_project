package com.smartfactory.energy.report;

import static com.smartfactory.energy.TestData.dayTariffs;
import static com.smartfactory.energy.TestData.interval;
import static com.smartfactory.energy.TestData.powers;
import static com.smartfactory.energy.TestData.recommendation;
import static com.smartfactory.energy.TestData.setting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.analysis.PeakAnalysisService;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.report.dto.ReportResponse;
import com.smartfactory.energy.report.dto.ReportTariffUsage;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffSeason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// 월간 성적표: month 검사, 기록 없는 달, 방어 성공률(같은 구간 중복·진행 중 구간), 요금 합계
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock SitePowerMinuteRepository sitePowerMinuteRepository;
    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @Mock EssStatusMinuteRepository essStatusMinuteRepository;
    @Mock RecommendationRepository recommendationRepository;
    @Mock TariffPeriodRepository tariffPeriodRepository;
    @Mock SettingRepository settingRepository;
    ReportService reportService;

    // 오늘 날짜에 흔들리지 않게 "지난달"로 시험함
    static final YearMonth MONTH = YearMonth.now().minusMonths(1);
    static final LocalDate DAY = MONTH.atDay(10);

    @BeforeEach
    void setUp() {
        // 성적표는 피크 분석 계산을 그대로 씀 → 진짜 PeakAnalysisService에 같은 가짜 Repository를 넣음
        PeakAnalysisService peakAnalysisService = new PeakAnalysisService(
                demandInterval15mRepository, recommendationRepository, settingRepository, tariffPeriodRepository);
        reportService = new ReportService(sitePowerMinuteRepository, demandInterval15mRepository,
                essStatusMinuteRepository, recommendationRepository, tariffPeriodRepository, settingRepository,
                peakAnalysisService);

        when(settingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(settingRepository.findById("peak.target_kw")).thenReturn(Optional.of(setting("peak.target_kw", "목표 피크", "1800")));
        when(settingRepository.findById("tariff.base_charge")).thenReturn(Optional.of(setting("tariff.base_charge", "기본요금", "8000")));
        for (TariffSeason season : TariffSeason.values()) {
            when(tariffPeriodRepository.findBySeason(season)).thenReturn(dayTariffs(season));
        }
        when(essStatusMinuteRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(sitePowerMinuteRepository.findInPeriod(any(), any())).thenReturn(List.of());
    }

    @Test
    void month_형식이_틀리면_400() {
        assertThatThrownBy(() -> reportService.getReport("2026-13")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportService.getReport("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 아직_안_온_달과_기록_없는_달은_hasData_false() {
        assertThat(reportService.getReport(YearMonth.now().plusMonths(1).toString()).hasData()).isFalse();

        ReportResponse empty = reportService.getReport(MONTH.toString());
        assertThat(empty.hasData()).isFalse();
        assertThat(empty.targetPeakKw()).isEqualTo(1800.0);
    }

    @Test
    void 방어_성공률은_구간_1개를_1회로_세고_진행_중_구간은_뺌() {
        givenMonthData();
        LocalDateTime w0900 = DAY.atTime(9, 0);
        LocalDateTime w1300 = DAY.atTime(13, 0);
        LocalDateTime w1330 = DAY.atTime(13, 30); // 뷰에 없음 = 아직 결과 없음
        when(recommendationRepository.findInPeriod(any(), any())).thenReturn(List.of(
                defense(1, w0900, RecommendationStatus.APPLIED),          // 1000 ≤ 1100 → 성공
                defense(2, w1300, RecommendationStatus.EXPIRED),          // 같은 구간 3줄 → 1회
                defense(3, w1300.plusMinutes(1), RecommendationStatus.APPLIED),
                defense(4, w1300.plusMinutes(2), RecommendationStatus.APPLIED), // 1200 > 1100 → 실패
                defense(5, w1330, RecommendationStatus.PENDING)           // 진행 중 → 빼고 셈
        ));

        ReportResponse report = reportService.getReport(MONTH.toString());

        assertThat(report.summary().defenseTotal()).isEqualTo(2);
        assertThat(report.summary().defenseSuccess()).isEqualTo(1);
        assertThat(report.summary().defenseSuccessPercent()).isEqualTo(50.0);
        assertThat(report.ess().blockedCount()).isEqualTo(1); // 13:00 구간 적용 2줄 → 1회
    }

    @Test
    void 요금_시간대별_금액의_합은_전력량요금과_같고_비중의_합은_100() {
        givenMonthData();

        ReportResponse report = reportService.getReport(MONTH.toString());

        // 경부하 600kW×30분=300kWh×100원, 중간 1000kW×30분=500kWh×150원, 최대 1200kW×30분=600kWh×200원
        assertThat(report.bill().energyCharge()).isEqualTo(30000 + 75000 + 120000);
        long sumOfTariffs = report.bill().byTariff().stream().mapToLong(ReportTariffUsage::charge).sum();
        assertThat(sumOfTariffs).isEqualTo(report.bill().energyCharge());
        double sumOfPercents = report.bill().byTariff().stream().mapToDouble(ReportTariffUsage::percent).sum();
        assertThat(sumOfPercents).isEqualTo(100.0);
        // 기본요금 = 이번 달 최대 피크 1200 × 8000원, 목표 1800을 넘지 않아 초과분 0
        assertThat(report.bill().baseCharge()).isEqualTo(9_600_000L);
        assertThat(report.bill().baseChargeOverTarget()).isZero();
        assertThat(report.bill().total()).isEqualTo(225000 + 9_600_000L);
        // 월말 예상: 기록된 90분 기준 하루 요금 × 그 달 날수 + 기본요금
        long expected = Math.round(225000 / (90 / 1440.0) * MONTH.lengthOfMonth()) + 9_600_000L;
        assertThat(report.bill().monthEndEstimate()).isEqualTo(expected);
    }

    // 지난달 10일: 01시 600kW 30분(경부하), 09시 1000kW 30분(중간), 13시 1200kW 30분(최대)
    private void givenMonthData() {
        List<SitePowerMinute> rows = new ArrayList<>();
        rows.addAll(powers(DAY.atTime(1, 0), 30, 600));
        rows.addAll(powers(DAY.atTime(9, 0), 30, 1000));
        rows.addAll(powers(DAY.atTime(13, 0), 30, 1200));
        LocalDateTime monthStart = MONTH.atDay(1).atStartOfDay();
        when(sitePowerMinuteRepository.findInPeriod(monthStart, MONTH.plusMonths(1).atDay(1).atStartOfDay()))
                .thenReturn(rows);
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of(
                interval(DAY.atTime(1, 0), 600, 600, 15),
                interval(DAY.atTime(1, 15), 600, 600, 15),
                interval(DAY.atTime(9, 0), 1000, 1000, 15),
                interval(DAY.atTime(9, 15), 1000, 1000, 15),
                interval(DAY.atTime(13, 0), 1200, 1200, 15),
                interval(DAY.atTime(13, 15), 1200, 1200, 15)
        ));
    }

    private static Recommendation defense(long id, LocalDateTime at, RecommendationStatus status) {
        LocalDateTime window = at.withMinute(at.getMinute() / 15 * 15);
        return recommendation(id, at, window, RecommendationAction.DISCHARGE,
                RecommendationReasonType.PEAK_DEFENSE, status, 1100);
    }
}
