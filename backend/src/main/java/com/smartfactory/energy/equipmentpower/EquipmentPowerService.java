package com.smartfactory.energy.equipmentpower;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.equipmentpower.dto.EquipmentPowerResponse;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.zone.Zone;
import com.smartfactory.energy.zone.ZoneRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentPowerService {

    // 한 번에 볼 수 있는 최대 시간 (하루)
    private static final int MAX_RANGE_HOURS = 24;

    private final EquipmentRepository equipmentRepository;
    private final ZoneRepository zoneRepository;
    private final PowerMinuteRepository powerMinuteRepository;

    // 기간 안의 15분 구간별 · 부하 설비별 평균 kW. 구간 시간 순, 같은 구간 안에서는 많이 쓴 순. from 이상 ~ to 미만
    public List<EquipmentPowerResponse> findInPeriod(LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("시작 시각이 끝 시각보다 빨라야 합니다.");
        }
        if (ChronoUnit.HOURS.between(from, to) > MAX_RANGE_HOURS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_RANGE_HOURS + "시간까지 조회할 수 있습니다.");
        }

        // 공장 부하 설비만 (한전·태양광·ESS는 표의 다른 칸에서 보여 줌)
        Map<String, Equipment> equipments = equipmentRepository.findByEquipmentRole(EquipmentRole.LOAD).stream()
                .collect(Collectors.toMap(Equipment::getEquipmentId, Function.identity()));
        Map<String, String> zoneNames = zoneRepository.findAll().stream()
                .collect(Collectors.toMap(Zone::getZoneCode, Zone::getName));

        // 15분 구간 시작 → 설비 → 1분 기록들
        Map<LocalDateTime, Map<String, List<PowerMinute>>> byWindow = powerMinuteRepository
                .findInPeriod(List.copyOf(equipments.keySet()), from, to).stream()
                .collect(Collectors.groupingBy(p -> windowStartOf(p.getTs()), TreeMap::new,
                        Collectors.groupingBy(PowerMinute::getEquipmentId)));

        List<EquipmentPowerResponse> result = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, List<PowerMinute>>> window : byWindow.entrySet()) {
            window.getValue().entrySet().stream()
                    .map(entry -> toResponse(window.getKey(), equipments.get(entry.getKey()), zoneNames, entry.getValue()))
                    .sorted(Comparator.comparing(EquipmentPowerResponse::avgKw).reversed())
                    .forEach(result::add);
        }
        return result;
    }

    // 시각이 들어 있는 15분 구간의 시작 (예: 14:22 → 14:15)
    private LocalDateTime windowStartOf(LocalDateTime ts) {
        LocalDateTime hour = ts.truncatedTo(ChronoUnit.HOURS);
        return hour.plusMinutes(ts.getMinute() / 15 * 15L);
    }

    // 1분 기록들을 평균 kW · 가동 비율로 묶음
    private EquipmentPowerResponse toResponse(LocalDateTime windowStart, Equipment e, Map<String, String> zoneNames,
                                              List<PowerMinute> rows) {
        double avgKw = rows.stream().mapToDouble(r -> r.getAvgKw().doubleValue()).average().orElse(0);
        long running = rows.stream().filter(r -> r.getIsRunning() != null && r.getIsRunning() == 1).count();
        return new EquipmentPowerResponse(
                windowStart,
                e.getEquipmentId(),
                e.getName(),
                e.getZoneCode(),
                zoneNames.get(e.getZoneCode()),
                round1(avgKw),
                round1(running * 100.0 / rows.size())
        );
    }

    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
