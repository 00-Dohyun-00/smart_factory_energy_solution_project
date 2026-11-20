package com.smartfactory.energy.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.ess.EssCommand;
import com.smartfactory.energy.ess.EssStatus;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.RawEquipmentLoadRepository;
import com.smartfactory.energy.power.RawFactoryLoad;
import com.smartfactory.energy.recommendation.RecommendationService;
import com.smartfactory.energy.setting.SettingCache;
import com.smartfactory.energy.solar.SolarPowerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

// ESS 출력이 매초 잔량(SOC)에 반영되는지. 소수 2자리로 매초 반올림하면 작은 1초 변화량이 깎여
// 쌓이지 않을 수 있어서(이슈: 방전 중에도 SOC가 안 움직임), 60초(틱 60번) 동안 쌓인 변화량을 확인함
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PowerSimulatorTest {

    @Mock RawLoadCursor rawLoadCursor;
    @Mock RawEquipmentLoadRepository rawEquipmentLoadRepository;
    @Mock SolarPowerRepository solarPowerRepository;
    @Mock RecommendationService recommendationService;
    @Mock EssStatusMinuteRepository essStatusMinuteRepository;
    @Mock SettingCache settingCache;

    MinuteBuffer minuteBuffer;
    EssSocState essSocState;
    PowerSimulator powerSimulator;

    @BeforeEach
    void setUp() {
        minuteBuffer = new MinuteBuffer();
        essSocState = new EssSocState(essStatusMinuteRepository);
        powerSimulator = new PowerSimulator(rawLoadCursor, rawEquipmentLoadRepository, solarPowerRepository,
                recommendationService, essSocState, settingCache, minuteBuffer);

        // 공장 부하를 충분히 크게(5,000kW) 둬서 방전이 역송 제한(공장 소비 - 태양광)에 안 걸리게 함
        when(rawLoadCursor.nextRow()).thenReturn(
                new RawFactoryLoad(LocalDateTime.now(), LocalDateTime.now(), BigDecimal.valueOf(5000)));
        when(rawEquipmentLoadRepository.findByRecordedAt(any())).thenReturn(List.of());
        when(solarPowerRepository.findFirstByPowerTypeOrderByTsDesc(any())).thenReturn(Optional.empty());

        when(settingCache.getNumber("ess.max_power_kw")).thenReturn(500.0);
        when(settingCache.getNumber("ess.soc_low")).thenReturn(20.0);
        when(settingCache.getNumber("ess.soc_high")).thenReturn(90.0);
        when(settingCache.getNumber("ess.efficiency_percent")).thenReturn(89.5);
        when(settingCache.getNumber("ess.capacity_kwh")).thenReturn(1000.0);
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            powerSimulator.tick();
        }
    }

    @Test
    void 방전_500kW로_60초_돌리면_잔량이_약_0_83포인트_줄어듦() {
        essSocState.set(50.0);
        when(recommendationService.currentCommand(any()))
                .thenReturn(new EssCommand(EssStatus.DISCHARGE, BigDecimal.valueOf(500)));

        tick(60);

        // 500kW × 60초/3600 ÷ 1,000kWh × 100 = 0.8333...
        assertThat(essSocState.getRaw()).isCloseTo(50.0 - 0.8333, within(0.01));
    }

    @Test
    void 방전_107kW로_60초_돌리면_잔량이_약_0_18포인트_줄어듦() {
        essSocState.set(50.0);
        when(recommendationService.currentCommand(any()))
                .thenReturn(new EssCommand(EssStatus.DISCHARGE, BigDecimal.valueOf(107)));

        tick(60);

        // 107kW × 60초/3600 ÷ 1,000kWh × 100 = 0.1783...
        assertThat(essSocState.getRaw()).isCloseTo(50.0 - 0.1783, within(0.01));
    }

    @Test
    void 충전_89kW로_60초_돌리면_효율_반영해서_잔량이_약_0_13포인트_늘어남() {
        essSocState.set(50.0);
        when(recommendationService.currentCommand(any()))
                .thenReturn(new EssCommand(EssStatus.CHARGE, BigDecimal.valueOf(89)));

        tick(60);

        // 89kW × 60초/3600 × 효율 0.895 ÷ 1,000kWh × 100 = 0.1328...
        assertThat(essSocState.getRaw()).isCloseTo(50.0 + 0.1328, within(0.01));
    }

    @Test
    void 대기_상태면_60초가_지나도_잔량이_그대로임() {
        essSocState.set(50.0);
        when(recommendationService.currentCommand(any())).thenReturn(EssCommand.idle());

        tick(60);

        assertThat(essSocState.getRaw()).isEqualTo(50.0);
    }
}
