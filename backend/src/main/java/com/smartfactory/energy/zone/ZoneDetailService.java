package com.smartfactory.energy.zone;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentCache;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.zone.dto.ZoneDetailResponse;
import com.smartfactory.energy.zone.dto.ZoneDetailResponse.ZonePoint;
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
    private final EquipmentCache equipmentCache;
    private final PowerMinuteRepository powerMinuteRepository;

    // 구역 상세 (zoneCode 예: Z2)
    public ZoneDetailResponse getDetail(String zoneCode) {
        // 1. 구역 찾기. 없으면 404
        Zone zone = zoneRepository.findAll().stream()
                .filter(z -> zoneCode.equalsIgnoreCase(z.getZoneCode())) // 구역 코드가 비어 있는 줄이 있어도 안전하게
                .findFirst()
                .orElseThrow(() -> new NotFoundException("없는 구역입니다: " + zoneCode));

        // 2. 그 구역의 생산 설비 (사용 중인 것만)
        List<String> equipmentIds = equipmentCache.findAll().stream()
                .filter(e -> zone.getZoneCode().equals(e.getZoneCode()))
                .filter(e -> e.getEquipmentRole() == EquipmentRole.LOAD)
                .map(Equipment::getEquipmentId)
                .toList();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay(); // now와 같은 날짜 기준 (자정 직전 호출 대비)
        if (equipmentIds.isEmpty()) {
            return new ZoneDetailResponse(zone.getZoneCode(), List.of(), null, null, 0.0);
        }

        // 3. 기록 꺼내기: 오늘 0시와 1시간 전 중 이른 시각부터 지금까지
        //    (0시 20분이면 최근 1시간 그래프에 어제 23시 20분 ~ 23시 59분도 들어가야 해서)
        LocalDateTime hourAgo = now.minusMinutes(60);
        LocalDateTime readFrom = hourAgo.isBefore(todayStart) ? hourAgo : todayStart;
        List<PowerMinute> rows = powerMinuteRepository.findInPeriod(equipmentIds, readFrom, now.plusMinutes(1));

        // 1분마다 구역 합계. 오늘 요약(최대·평균·사용량)은 오늘 기록만 씀
        Map<LocalDateTime, Double> kwByMinute = new TreeMap<>();
        Map<LocalDateTime, Double> todayKwByMinute = new TreeMap<>();
        double todayKwh = 0;
        for (PowerMinute p : rows) {
            double kw = p.getAvgKw().doubleValue();
            kwByMinute.merge(p.getTs(), kw, Double::sum);
            if (!p.getTs().isBefore(todayStart)) {
                todayKwByMinute.merge(p.getTs(), kw, Double::sum);
                todayKwh += kw / 60; // 1분 kW → kWh
            }
        }

        // 4. 최근 1시간 (지금 포함 61칸 중 기록 있는 분만)
        List<ZonePoint> points = new ArrayList<>();
        for (LocalDateTime ts : kwByMinute.keySet()) {
            if (!ts.isBefore(hourAgo)) {
                points.add(new ZonePoint(ts, round1(kwByMinute.get(ts))));
            }
        }

        Double todayMaxKw = todayKwByMinute.isEmpty() ? null
                : round1(todayKwByMinute.values().stream().mapToDouble(Double::doubleValue).max().getAsDouble());
        Double todayAvgKw = todayKwByMinute.isEmpty() ? null
                : round1(todayKwByMinute.values().stream().mapToDouble(Double::doubleValue).average().getAsDouble());

        return new ZoneDetailResponse(zone.getZoneCode(), points, todayMaxKw, todayAvgKw, round1(todayKwh));
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
