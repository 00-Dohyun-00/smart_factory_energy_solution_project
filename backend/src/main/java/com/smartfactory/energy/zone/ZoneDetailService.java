package com.smartfactory.energy.zone;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.zone.dto.ZoneDetailResponse;
import com.smartfactory.energy.zone.dto.ZoneDetailResponse.ZonePoint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneDetailService {

    private final ZoneRepository zoneRepository;
    private final EquipmentRepository equipmentRepository;
    private final PowerMinuteRepository powerMinuteRepository;

    // 구역 상세 (zoneCode 예: Z2)
    public ZoneDetailResponse getDetail(String zoneCode) {
        // 1. 구역 찾기. 없으면 404
        Zone zone = zoneRepository.findAll().stream()
                .filter(z -> z.getZoneCode().equalsIgnoreCase(zoneCode))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("없는 구역입니다: " + zoneCode));

        // 2. 그 구역의 생산 설비 (사용 중인 것만)
        List<String> equipmentIds = equipmentRepository.findAll().stream()
                .filter(e -> zone.getZoneId().equals(e.getZoneId()))
                .filter(e -> e.getEquipmentRole() == EquipmentRole.LOAD)
                .filter(Equipment::getIsActive)
                .map(Equipment::getEquipmentId)
                .toList();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        if (equipmentIds.isEmpty()) {
            return new ZoneDetailResponse(zone.getZoneCode(), List.of(), null, null, 0.0);
        }

        // 3. 오늘 기록 → 1분마다 구역 합계. 사용량은 energy_kwh 합
        List<PowerMinute> todayRows = powerMinuteRepository.findInPeriod(equipmentIds, todayStart, now.plusMinutes(1));
        Map<LocalDateTime, Double> kwByMinute = new TreeMap<>();
        double todayKwh = 0;
        for (PowerMinute p : todayRows) {
            kwByMinute.merge(p.getTs(), p.getAvgKw().doubleValue(), Double::sum);
            if (p.getEnergyKwh() != null) {
                todayKwh += p.getEnergyKwh().doubleValue();
            }
        }

        // 4. 최근 1시간 (지금 포함 61칸 중 기록 있는 분만)
        LocalDateTime hourAgo = now.minusMinutes(60);
        List<ZonePoint> points = new ArrayList<>();
        for (LocalDateTime ts : kwByMinute.keySet()) {
            if (!ts.isBefore(hourAgo)) {
                points.add(new ZonePoint(ts, round1(kwByMinute.get(ts))));
            }
        }

        Double todayMaxKw = kwByMinute.isEmpty() ? null
                : round1(kwByMinute.values().stream().mapToDouble(Double::doubleValue).max().getAsDouble());
        Double todayAvgKw = kwByMinute.isEmpty() ? null
                : round1(kwByMinute.values().stream().mapToDouble(Double::doubleValue).average().getAsDouble());

        return new ZoneDetailResponse(zone.getZoneCode(), points, todayMaxKw, todayAvgKw, round1(todayKwh));
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
