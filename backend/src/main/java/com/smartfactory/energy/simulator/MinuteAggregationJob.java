package com.smartfactory.energy.simulator;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.ess.EssStatus;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteId;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 1분(60초)간 MinuteBuffer에 모인 값을 평균/최대/최소/kWh로 집계해서 실시간 테이블에 저장합니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class MinuteAggregationJob {

    private static final int KW_SCALE = 2;
    private static final int KWH_SCALE = 4;

    private final SimClock simClock;
    private final MinuteBuffer minuteBuffer;
    private final EquipmentRepository equipmentRepository;
    private final PowerMinuteRepository powerMinuteRepository;
    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final EssStatusMinuteRepository essStatusMinuteRepository;

    private List<String> loadEquipmentIds;

    @Scheduled(cron = "0 * * * * *")
    public void aggregateAndSave() {
        if (minuteBuffer.isEmpty()) {
            return;
        }
        LocalDateTime ts = simClock.now().truncatedTo(ChronoUnit.MINUTES);

        saveSitePowerMinute(ts);
        savePowerMinute(ts);
        saveEssStatusMinute(ts);

        minuteBuffer.clear();
    }

    private void saveSitePowerMinute(LocalDateTime ts) {
        if (sitePowerMinuteRepository.existsById(ts)) {
            log.debug("site_power_minute {} 이미 있어서 건너뜀", ts);
            return;
        }
        BigDecimal gridKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.GRID_EQUIPMENT_ID));
        BigDecimal solarKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.SOLAR_EQUIPMENT_ID));
        BigDecimal essKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.ESS_EQUIPMENT_ID));
        BigDecimal loadKw = loadEquipmentIds().stream()
                .map(id -> avgOf(minuteBuffer.samplesOf(id)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sitePowerMinuteRepository.save(new SitePowerMinute(ts, gridKw, solarKw, essKw, loadKw));
    }

    private void savePowerMinute(LocalDateTime ts) {
        BigDecimal siteGridKwh = energyKwh(avgOf(minuteBuffer.samplesOf(PowerSimulator.GRID_EQUIPMENT_ID)));
        BigDecimal siteSolarKwh = energyKwh(avgOf(minuteBuffer.samplesOf(PowerSimulator.SOLAR_EQUIPMENT_ID)));
        BigDecimal siteEssKwh = energyKwh(avgOf(minuteBuffer.samplesOf(PowerSimulator.ESS_EQUIPMENT_ID)));

        record LoadResult(String equipmentId, BigDecimal avg, BigDecimal max, BigDecimal min, BigDecimal energyKwh) {
        }

        List<LoadResult> loadResults = loadEquipmentIds().stream()
                .map(id -> {
                    List<BigDecimal> s = minuteBuffer.samplesOf(id);
                    BigDecimal avg = avgOf(s);
                    return new LoadResult(id, avg, maxOf(s), minOf(s), energyKwh(avg));
                })
                .toList();

        BigDecimal totalLoadEnergyKwh = loadResults.stream()
                .map(LoadResult::energyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (LoadResult r : loadResults) {
            PowerMinuteId id = new PowerMinuteId(r.equipmentId(), ts);
            if (powerMinuteRepository.existsById(id)) {
                continue;
            }
            // 이 설비가 그 1분간 전체 LOAD 소비 에너지에서 차지하는 비율만큼, 사이트 전체 에너지원을 나눠서 배분
            BigDecimal share = totalLoadEnergyKwh.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : r.energyKwh().divide(totalLoadEnergyKwh, 8, RoundingMode.HALF_UP);
            PowerMinute powerMinute = new PowerMinute(
                    r.equipmentId(), ts, r.avg(), r.max(), r.min(), r.energyKwh(),
                    siteGridKwh.multiply(share).setScale(KWH_SCALE, RoundingMode.HALF_UP),
                    siteSolarKwh.multiply(share).setScale(KWH_SCALE, RoundingMode.HALF_UP),
                    siteEssKwh.multiply(share).setScale(KWH_SCALE, RoundingMode.HALF_UP),
                    r.avg().compareTo(BigDecimal.ZERO) > 0 ? (short) 1 : (short) 0);
            powerMinuteRepository.save(powerMinute);
        }

        saveChannelEquipmentPowerMinute(ts, PowerSimulator.GRID_EQUIPMENT_ID);
        saveChannelEquipmentPowerMinute(ts, PowerSimulator.SOLAR_EQUIPMENT_ID);
        saveChannelEquipmentPowerMinute(ts, PowerSimulator.ESS_EQUIPMENT_ID);
    }

    // GRID-01/SOLAR-01/ESS-01은 그 자체가 하나의 에너지 채널이라 grid/solar/ess_kwh 배분 대상이 아니므로 null로 둡니다.
    private void saveChannelEquipmentPowerMinute(LocalDateTime ts, String equipmentId) {
        PowerMinuteId id = new PowerMinuteId(equipmentId, ts);
        if (powerMinuteRepository.existsById(id)) {
            return;
        }
        List<BigDecimal> samples = minuteBuffer.samplesOf(equipmentId);
        BigDecimal avg = avgOf(samples);
        PowerMinute powerMinute = new PowerMinute(
                equipmentId, ts, avg, maxOf(samples), minOf(samples), energyKwh(avg),
                null, null, null,
                avg.compareTo(BigDecimal.ZERO) > 0 ? (short) 1 : (short) 0);
        powerMinuteRepository.save(powerMinute);
    }

    private void saveEssStatusMinute(LocalDateTime ts) {
        if (essStatusMinuteRepository.existsById(ts)) {
            log.debug("ess_status_minute {} 이미 있어서 건너뜀", ts);
            return;
        }
        BigDecimal batteryTemp = BigDecimal.valueOf(23)
                .add(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(-0.5, 0.5)))
                .setScale(2, RoundingMode.HALF_UP);
        EssStatusMinute status = new EssStatusMinute(
                ts,
                BigDecimal.ZERO.setScale(KW_SCALE, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(KWH_SCALE, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(KWH_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(60).setScale(2, RoundingMode.HALF_UP),
                batteryTemp,
                EssStatus.IDLE);
        essStatusMinuteRepository.save(status);
    }

    private List<String> loadEquipmentIds() {
        if (loadEquipmentIds == null) {
            loadEquipmentIds = equipmentRepository.findByEquipmentRole(EquipmentRole.LOAD).stream()
                    .map(Equipment::getEquipmentId)
                    .toList();
        }
        return loadEquipmentIds;
    }

    private BigDecimal energyKwh(BigDecimal avgKw) {
        return avgKw.divide(BigDecimal.valueOf(60), KWH_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal avgOf(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(KW_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), KW_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal maxOf(List<BigDecimal> values) {
        return values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).setScale(KW_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal minOf(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).setScale(KW_SCALE, RoundingMode.HALF_UP);
    }
}
