package com.smartfactory.energy.equipmentpower.dto;

import java.time.LocalDateTime;

/**
 * 15분 구간 1개 × 설비 1대의 평균 전력 (전력 이력 표용).
 */
public record EquipmentPowerResponse(
        LocalDateTime windowStart, // 15분 구간 시작 시각
        String equipmentId,
        String name,
        String zoneCode,
        String zoneName,
        Double avgKw,              // 그 15분 평균 kW
        Double runningPercent      // 그 15분 중 가동한 분의 비율 %
) {
}
