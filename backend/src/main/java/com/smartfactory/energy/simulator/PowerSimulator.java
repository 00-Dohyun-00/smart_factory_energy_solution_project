package com.smartfactory.energy.simulator;

import com.smartfactory.energy.ess.EssCommand;
import com.smartfactory.energy.ess.EssCommandHolder;
import com.smartfactory.energy.power.RawEquipmentLoad;
import com.smartfactory.energy.power.RawEquipmentLoadRepository;
import com.smartfactory.energy.power.RawFactoryLoad;
import com.smartfactory.energy.setting.Setting;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.solar.SolarPower;
import com.smartfactory.energy.solar.SolarPowerRepository;
import com.smartfactory.energy.solar.SolarPowerType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// raw_factory_load를 1초에 한 행씩 재생해서, 이번 초의 설비별 kW / 공장 전체 kW / 태양광 / ESS 표본을
// MinuteBuffer에 쌓습니다. grid_kw는 여기서 계산하지 않고, 1분 집계 때 load/solar/ess 평균으로 계산합니다.
// 팀 공용 DB라서 시뮬레이터는 한 명(SIMULATOR_ENABLED=true)만 돌립니다.
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PowerSimulator {

    // 공장 전체 소비(factory_kw) 표본을 모아두는 키. 실제 설비 ID가 아니라 MinuteBuffer 안에서만 쓰는 이름입니다.
    public static final String FACTORY_LOAD_KEY = "FACTORY_LOAD";
    public static final String SOLAR_EQUIPMENT_ID = "SOLAR-01";
    public static final String ESS_EQUIPMENT_ID = "ESS-01";

    // setting에 값이 없을 때 쓰는 기본값 (설정 화면 기본값과 동일)
    private static final List<String> ESS_SETTING_KEYS = List.of(
            "ess.max_power_kw", "ess.soc_low", "ess.soc_high", "ess.efficiency_percent", "ess.capacity_kwh");
    private static final double DEFAULT_MAX_POWER_KW = 500;
    private static final double DEFAULT_SOC_LOW = 20;
    private static final double DEFAULT_SOC_HIGH = 90;
    private static final double DEFAULT_EFFICIENCY_PERCENT = 89.5;
    private static final double DEFAULT_CAPACITY_KWH = 1000;

    private final RawLoadCursor rawLoadCursor;
    private final RawEquipmentLoadRepository rawEquipmentLoadRepository;
    private final SolarPowerRepository solarPowerRepository;
    private final EssCommandHolder essCommandHolder;
    private final EssSocState essSocState;
    private final SettingRepository settingRepository;
    private final MinuteBuffer minuteBuffer;

    @Scheduled(fixedRate = 1000)
    public void tick() {
        RawFactoryLoad row = rawLoadCursor.nextRow();
        BigDecimal loadKw = row.getFactoryKw();
        minuteBuffer.addSample(FACTORY_LOAD_KEY, loadKw);

        List<RawEquipmentLoad> loadRows = rawEquipmentLoadRepository.findByRecordedAt(row.getRecordedAt());
        for (RawEquipmentLoad loadRow : loadRows) {
            minuteBuffer.addSample(loadRow.getEquipmentId(), loadRow.getKw());
        }

        BigDecimal solarKw = solarPowerRepository.findFirstByPowerTypeOrderByTsDesc(SolarPowerType.SOLAR)
                .map(SolarPower::getPowerKw)
                .orElse(BigDecimal.ZERO);
        minuteBuffer.addSample(SOLAR_EQUIPMENT_ID, solarKw);

        BigDecimal essKw = applyEss(loadKw, solarKw);
        minuteBuffer.addSample(ESS_EQUIPMENT_ID, essKw);
    }

    // ESS 지시값(EssCommandHolder)에 제한(최대 출력, SOC 하한·상한, 역송 금지)을 적용해서 실제 출력을 구하고,
    // 그만큼 SOC를 갱신합니다. 출력은 + 방전 / - 충전입니다.
    private BigDecimal applyEss(BigDecimal loadKw, BigDecimal solarKw) {
        Map<String, Double> settings = essSettings();
        double maxPowerKw = settings.getOrDefault("ess.max_power_kw", DEFAULT_MAX_POWER_KW);
        double socLow = settings.getOrDefault("ess.soc_low", DEFAULT_SOC_LOW);
        double socHigh = settings.getOrDefault("ess.soc_high", DEFAULT_SOC_HIGH);
        double efficiency = settings.getOrDefault("ess.efficiency_percent", DEFAULT_EFFICIENCY_PERCENT) / 100;
        double capacityKwh = settings.getOrDefault("ess.capacity_kwh", DEFAULT_CAPACITY_KWH);
        double soc = essSocState.get().doubleValue();

        EssCommand command = essCommandHolder.get();
        double desiredKw = switch (command.status()) {
            case DISCHARGE -> command.powerKw().doubleValue();
            case CHARGE -> -command.powerKw().doubleValue();
            case IDLE -> 0;
        };

        // 최대 출력 제한
        desiredKw = Math.max(-maxPowerKw, Math.min(maxPowerKw, desiredKw));
        // 방전은 SOC > soc_low일 때만, 충전은 SOC < soc_high일 때만
        if (desiredKw > 0 && soc <= socLow) {
            desiredKw = 0;
        }
        if (desiredKw < 0 && soc >= socHigh) {
            desiredKw = 0;
        }
        // 방전은 역송(grid 음수)이 없도록 공장 소비 - 태양광을 넘지 않게 제한
        if (desiredKw > 0) {
            double maxDischargeKw = Math.max(loadKw.doubleValue() - solarKw.doubleValue(), 0);
            desiredKw = Math.min(desiredKw, maxDischargeKw);
        }

        // SOC 갱신: 1초 충전량 = max(-ess,0)/3600, 1초 방전량 = max(ess,0)/3600
        double chargeKwh = Math.max(-desiredKw, 0) / 3600;
        double dischargeKwh = Math.max(desiredKw, 0) / 3600;
        double newSoc = soc + (chargeKwh * efficiency - dischargeKwh) / capacityKwh * 100;
        newSoc = Math.max(socLow, Math.min(socHigh, newSoc));
        essSocState.set(BigDecimal.valueOf(newSoc).setScale(2, RoundingMode.HALF_UP));

        return BigDecimal.valueOf(desiredKw).setScale(2, RoundingMode.HALF_UP);
    }

    // ESS 관련 setting 값을 한 번의 조회로 숫자로 읽음. 값이 없는 키는 기본값을 씀
    private Map<String, Double> essSettings() {
        Map<String, Double> values = new HashMap<>();
        for (Setting setting : settingRepository.findAllById(ESS_SETTING_KEYS)) {
            values.put(setting.getSettingKey(), Double.parseDouble(setting.getValue()));
        }
        return values;
    }
}
