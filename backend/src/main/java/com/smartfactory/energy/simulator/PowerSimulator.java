package com.smartfactory.energy.simulator;

import com.smartfactory.energy.ess.EssCommand;
import com.smartfactory.energy.ess.EssCommandHolder;
import com.smartfactory.energy.ess.EssStatus;
import com.smartfactory.energy.power.RawEquipmentLoad;
import com.smartfactory.energy.power.RawEquipmentLoadRepository;
import com.smartfactory.energy.power.RawFactoryLoad;
import com.smartfactory.energy.solar.SolarPower;
import com.smartfactory.energy.solar.SolarPowerRepository;
import com.smartfactory.energy.solar.SolarPowerType;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// raw_factory_load를 1초에 한 행씩 재생해서, 이번 초의 설비별/공장 전체 kW를 계산해 MinuteBuffer에 쌓습니다.
// 팀 공용 DB라서 시뮬레이터는 한 명(SIMULATOR_ENABLED=true)만 돌립니다.
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PowerSimulator {

    public static final String GRID_EQUIPMENT_ID = "GRID-01";
    public static final String SOLAR_EQUIPMENT_ID = "SOLAR-01";
    public static final String ESS_EQUIPMENT_ID = "ESS-01";

    private final RawLoadCursor rawLoadCursor;
    private final RawEquipmentLoadRepository rawEquipmentLoadRepository;
    private final SolarPowerRepository solarPowerRepository;
    private final EssCommandHolder essCommandHolder;
    private final MinuteBuffer minuteBuffer;

    @Scheduled(fixedRate = 1000)
    public void tick() {
        RawFactoryLoad row = rawLoadCursor.nextRow();

        List<RawEquipmentLoad> loadRows = rawEquipmentLoadRepository.findByRecordedAt(row.getRecordedAt());
        BigDecimal loadKw = BigDecimal.ZERO;
        for (RawEquipmentLoad loadRow : loadRows) {
            minuteBuffer.addSample(loadRow.getEquipmentId(), loadRow.getKw());
            loadKw = loadKw.add(loadRow.getKw());
        }

        BigDecimal solarKw = solarPowerRepository.findFirstByPowerTypeOrderByTsDesc(SolarPowerType.SOLAR)
                .map(SolarPower::getPowerKw)
                .orElse(BigDecimal.ZERO);

        // ESS는 이번 작업에서는 항상 대기(IDLE/0). 나중에 판단 엔진이 EssCommandHolder.set()으로 값을 넣으면
        // 여기서 바로 반영됩니다.
        EssCommand essCommand = essCommandHolder.get();
        BigDecimal essChargeKw = essCommand.status() == EssStatus.CHARGE ? essCommand.powerKw() : BigDecimal.ZERO;
        BigDecimal essDischargeKw = essCommand.status() == EssStatus.DISCHARGE ? essCommand.powerKw() : BigDecimal.ZERO;
        BigDecimal essKw = essDischargeKw.subtract(essChargeKw);

        BigDecimal gridKw = loadKw.subtract(solarKw).subtract(essDischargeKw).add(essChargeKw);
        if (gridKw.compareTo(BigDecimal.ZERO) < 0) {
            gridKw = BigDecimal.ZERO;
        }

        minuteBuffer.addSample(GRID_EQUIPMENT_ID, gridKw);
        minuteBuffer.addSample(SOLAR_EQUIPMENT_ID, solarKw);
        minuteBuffer.addSample(ESS_EQUIPMENT_ID, essKw);
    }
}
