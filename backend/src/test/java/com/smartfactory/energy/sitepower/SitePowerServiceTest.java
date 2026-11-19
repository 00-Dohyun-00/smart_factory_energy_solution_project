package com.smartfactory.energy.sitepower;

import static com.smartfactory.energy.TestData.dayTariffs;
import static com.smartfactory.energy.TestData.ess;
import static com.smartfactory.energy.TestData.power;
import static com.smartfactory.energy.TestData.setting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse;
import com.smartfactory.energy.sitepower.dto.SitePowerQuarterResponse;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.weather.WeatherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

// 지금 상태·오늘 15분 목록: 기록 없음 404, 예상 피크·단계·추가 방전, 요금 시간대, 15분 평균과 마지막 잔량
// "지금"은 서버 시계라서, 기록도 지금 시각을 기준으로 만듦
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SitePowerServiceTest {

    @Mock SitePowerMinuteRepository sitePowerMinuteRepository;
    @Mock EssStatusMinuteRepository essStatusMinuteRepository;
    @Mock DemandInterval15mRepository demandInterval15mRepository;
    @Mock PowerMinuteRepository powerMinuteRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock WeatherRepository weatherRepository;
    @Mock TariffPeriodRepository tariffPeriodRepository;
    @Mock SettingRepository settingRepository;
    @InjectMocks SitePowerService sitePowerService;

    // 가짜 DB 표: findInPeriod(start, end)는 이 목록에서 기간 안의 줄만 돌려줌
    List<SitePowerMinute> powerTable = new ArrayList<>();
    List<EssStatusMinute> essTable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(sitePowerMinuteRepository.findInPeriod(any(), any())).thenAnswer(call -> {
            LocalDateTime start = call.getArgument(0);
            LocalDateTime end = call.getArgument(1);
            return powerTable.stream().filter(p -> !p.getTs().isBefore(start) && p.getTs().isBefore(end)).toList();
        });
        when(essStatusMinuteRepository.findInPeriod(any(), any())).thenAnswer(call -> {
            LocalDateTime start = call.getArgument(0);
            LocalDateTime end = call.getArgument(1);
            return essTable.stream().filter(e -> !e.getTs().isBefore(start) && e.getTs().isBefore(end)).toList();
        });
        when(sitePowerMinuteRepository.findFirstByOrderByTsDesc())
                .thenAnswer(call -> powerTable.isEmpty() ? Optional.empty() : Optional.of(powerTable.get(powerTable.size() - 1)));
        when(essStatusMinuteRepository.findFirstByOrderByTsDesc()).thenReturn(Optional.empty());
        when(demandInterval15mRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(powerMinuteRepository.findByTs(any())).thenReturn(List.of());
        when(equipmentRepository.findByEquipmentRole(any())).thenReturn(List.of());
        when(weatherRepository.findInPeriod(any(), any())).thenReturn(List.of());
        when(tariffPeriodRepository.findBySeason(any())).thenAnswer(call -> dayTariffs(call.getArgument(0)));
        when(settingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(settingRepository.findById("peak.target_kw")).thenReturn(Optional.of(setting("peak.target_kw", "목표 피크", "1800")));
        when(settingRepository.findById("peak.warn_ratio")).thenReturn(Optional.of(setting("peak.warn_ratio", "주의선", "97")));
    }

    @Test
    void 전력_기록이_하나도_없으면_404() {
        assertThatThrownBy(() -> sitePowerService.getLatest()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 예상_피크는_쌓인_값과_지금_값으로_남은_분을_채운_평균() {
        // 지금 15분 구간에 지난 분까지 1700 kW, 바로 전 1분만 2000 kW
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime windowStart = now.withMinute(now.getMinute() / 15 * 15);
        powerTable.add(power(windowStart.minusMinutes(2), 1500, 0, 0, 1500)); // 지난 구간 (안 셈)
        for (LocalDateTime t = windowStart; t.isBefore(now.minusMinutes(1)); t = t.plusMinutes(1)) {
            powerTable.add(power(t, 1700, 0, 0, 1700));
        }
        // 가장 최근 1분 = 2000 kW (구간이 막 시작했으면 지난 구간의 마지막 1분)
        LocalDateTime lastMinute = now.minusMinutes(1);
        powerTable.add(power(lastMinute, 2000, 0, 0, 2000));

        SitePowerLatestResponse latest = sitePowerService.getLatest();

        // 기대값을 같은 식으로 계산: (구간 안 grid 합 + 최신 grid × 남은 분) ÷ 15
        List<SitePowerMinute> inWindow = powerTable.stream().filter(p -> !p.getTs().isBefore(windowStart)).toList();
        double sum = inWindow.stream().mapToDouble(p -> p.getGridKw().doubleValue()).sum();
        double expected = (sum + 2000 * (15 - inWindow.size())) / 15;
        assertThat(latest.predictedKw()).isEqualTo(Math.round(expected * 10) / 10.0);
        assertThat(latest.windowStart()).isEqualTo(windowStart);
        assertThat(latest.windowPoints()).hasSize(inWindow.size());
        assertThat(latest.warnKw()).isEqualTo(1746.0);
        // 예상이 1800 초과면 CRITICAL, 1746 이상이면 WARNING
        String expectedStatus = expected > 1800 ? "CRITICAL" : expected >= 1746 ? "WARNING" : "NORMAL";
        assertThat(latest.peakStatus()).isEqualTo(expectedStatus);
        assertThat(latest.pcsStatus()).isEqualTo("STANDBY"); // ESS 기록이 없으면 대기로 봄
    }

    @Test
    void 목표를_넘으면_남은_시간_안에_줄일_kW를_올림해서_줌() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime windowStart = now.withMinute(now.getMinute() / 15 * 15);
        powerTable.add(power(windowStart.minusMinutes(1), 2100, 0, 0, 2100));
        for (LocalDateTime t = windowStart; t.isBefore(now); t = t.plusMinutes(1)) {
            powerTable.add(power(t, 2100, 0, 0, 2100));
        }
        int remaining = 15 - (int) powerTable.stream().filter(p -> !p.getTs().isBefore(windowStart)).count();

        SitePowerLatestResponse latest = sitePowerService.getLatest();

        // 예상 2100, 목표 1800 → 15분 평균을 300 낮추려면 남은 분 동안 300 × 15 ÷ 남은 분만큼 더 방전
        assertThat(latest.predictedKw()).isEqualTo(2100.0);
        assertThat(latest.peakStatus()).isEqualTo("CRITICAL");
        assertThat(latest.defenseKw()).isEqualTo(Math.ceil(300.0 * 15 / remaining));
    }

    @Test
    void 요금표의_하루_끝은_24시로_보내고_다음_시간대는_이름이_바뀌는_칸() {
        powerTable.add(power(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusMinutes(1), 1000, 0, 0, 1000));

        SitePowerLatestResponse latest = sitePowerService.getLatest();

        assertThat(latest.tariff().periods()).hasSize(7);
        assertThat(latest.tariff().periods().get(6).to()).isEqualTo("24:00");
        assertThat(latest.tariff().nextLabel()).isNotEqualTo(latest.tariff().label());
    }

    @Test
    void 오늘_목록은_15분마다_평균과_구간_마지막_잔량() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        powerTable.add(power(start, 1000, 0, 0, 1000));
        powerTable.add(power(start.plusMinutes(1), 2000, 0, 0, 2000));
        powerTable.add(power(start.plusMinutes(15), 1500, 0, 0, 1500));
        essTable.add(ess(start, 50));
        essTable.add(ess(start.plusMinutes(14), 48));

        List<SitePowerQuarterResponse> today = sitePowerService.getToday();

        assertThat(today).hasSize(2);
        assertThat(today.get(0).gridKw()).isEqualTo(1500.0);
        assertThat(today.get(0).socPercent()).isEqualTo(48.0);
        assertThat(today.get(1).recordedAt()).isEqualTo(start.plusMinutes(15));
        assertThat(today.get(1).socPercent()).isNull(); // 그 구간 ESS 기록이 없음
    }
}
