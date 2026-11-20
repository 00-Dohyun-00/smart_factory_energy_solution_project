package com.smartfactory.energy.simulator;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentCache;
import com.smartfactory.energy.equipment.EquipmentRole;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 1분(60초)간 MinuteBuffer에 모인 값을 평균으로 집계해서 실시간 테이블에 저장합니다.
// 팀 공용 DB라서 시뮬레이터는 한 명(SIMULATOR_ENABLED=true)만 돌립니다.
@Slf4j
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MinuteAggregationJob {

    private static final int KW_SCALE = 2;

    // power_minute 15대 합을 load_kw에 정확히 맞출 때, 반올림 차이를 더해 넣을 설비
    private static final String ROUNDING_ADJUST_EQUIPMENT_ID = "BRZ-01";

    private final SimClock simClock;
    private final MinuteBuffer minuteBuffer;
    private final EquipmentCache equipmentCache;
    private final PowerMinuteRepository powerMinuteRepository;
    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final EssStatusMinuteRepository essStatusMinuteRepository;
    private final EssSocState essSocState;
    private final LatestSiteStatus latestSiteStatus;

    @Scheduled(cron = "0 * * * * *")
    public void aggregateAndSave() {
        if (minuteBuffer.isEmpty()) {
            return;
        }
        LocalDateTime ts = simClock.now().truncatedTo(ChronoUnit.MINUTES);

        // 공장 소비 = 이번 1분간 재생한 raw_factory_load.factory_kw 60개의 평균
        BigDecimal loadKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.FACTORY_LOAD_KEY));
        BigDecimal solarKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.SOLAR_EQUIPMENT_ID));
        BigDecimal essKw = avgOf(minuteBuffer.samplesOf(PowerSimulator.ESS_EQUIPMENT_ID));
        // 한전 구매 = 소비 - 태양광 - ESS(+ 방전 / - 충전). 태양광/ESS가 소비보다 많으면 한전 구매는 0
        BigDecimal gridKw = loadKw.subtract(solarKw).subtract(essKw);
        if (gridKw.compareTo(BigDecimal.ZERO) < 0) {
            gridKw = BigDecimal.ZERO;
        }

        saveSitePowerMinute(ts, gridKw, solarKw, essKw, loadKw);
        savePowerMinute(ts, loadKw);
        saveEssStatusMinute(ts);

        minuteBuffer.clear();
    }

    private void saveSitePowerMinute(LocalDateTime ts, BigDecimal gridKw, BigDecimal solarKw, BigDecimal essKw,
                                      BigDecimal loadKw) {
        if (sitePowerMinuteRepository.existsById(ts)) {
            log.debug("site_power_minute {} 이미 있어서 건너뜀", ts);
            return;
        }
        // 방금 저장한 값을 메모리에 들고 있어서, SitePowerService가 이번 분 "최신 1행"을 DB에서 다시 안 읽어도 됨
        latestSiteStatus.setSitePowerMinute(
                sitePowerMinuteRepository.save(new SitePowerMinute(ts, gridKw, solarKw, essKw, loadKw)));
    }

    private void savePowerMinute(LocalDateTime ts, BigDecimal loadKw) {
        record LoadResult(String equipmentId, BigDecimal avg) {
        }

        List<LoadResult> loadResults = new ArrayList<>(loadEquipmentIds().stream()
                .map(id -> new LoadResult(id, avgOf(minuteBuffer.samplesOf(id))))
                .toList());

        // 15대를 각각 반올림(avgOf가 이미 소수 2자리로 반올림)한 뒤 합이 load_kw보다 남거나 모자라면,
        // 그 차이를 BRZ-01에 더해서 합이 load_kw와 0.00까지 정확히 같아지게 맞춥니다.
        BigDecimal sumOfAvg = loadResults.stream().map(LoadResult::avg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = loadKw.subtract(sumOfAvg);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            boolean adjusted = false;
            for (int i = 0; i < loadResults.size(); i++) {
                LoadResult r = loadResults.get(i);
                if (r.equipmentId().equals(ROUNDING_ADJUST_EQUIPMENT_ID)) {
                    loadResults.set(i, new LoadResult(r.equipmentId(), r.avg().add(diff)));
                    adjusted = true;
                    break;
                }
            }
            if (!adjusted) {
                log.warn("[POWER-MINUTE] {} 설비를 찾지 못해 load_kw 반올림 차이({})를 맞추지 못함",
                        ROUNDING_ADJUST_EQUIPMENT_ID, diff);
            }
        }

        for (LoadResult r : loadResults) {
            PowerMinuteId id = new PowerMinuteId(r.equipmentId(), ts);
            if (powerMinuteRepository.existsById(id)) {
                continue;
            }
            PowerMinute powerMinute = new PowerMinute(
                    r.equipmentId(), ts, r.avg(),
                    r.avg().compareTo(BigDecimal.ZERO) > 0 ? (short) 1 : (short) 0);
            powerMinuteRepository.save(powerMinute);
        }
    }

    private void saveEssStatusMinute(LocalDateTime ts) {
        if (essStatusMinuteRepository.existsById(ts)) {
            log.debug("ess_status_minute {} 이미 있어서 건너뜀", ts);
            return;
        }
        BigDecimal batteryTemp = BigDecimal.valueOf(23)
                .add(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(-0.5, 0.5)))
                .setScale(2, RoundingMode.HALF_UP);
        latestSiteStatus.setEssStatusMinute(
                essStatusMinuteRepository.save(new EssStatusMinute(ts, essSocState.get(), batteryTemp)));
    }

    // EquipmentCache가 이미 서버 시작 때부터 메모리에 들고 있어서, 여기서 따로 캐시하지 않음
    private List<String> loadEquipmentIds() {
        return equipmentCache.findByEquipmentRole(EquipmentRole.LOAD).stream()
                .map(Equipment::getEquipmentId)
                .toList();
    }

    private BigDecimal avgOf(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(KW_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), KW_SCALE, RoundingMode.HALF_UP);
    }
}
