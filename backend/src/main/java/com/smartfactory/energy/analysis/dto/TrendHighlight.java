package com.smartfactory.energy.analysis.dto;

import java.time.LocalDateTime;

/**
 * 눈에 띄는 순간 1개 (예: 15분 피크 최대, 배터리 잔량 최저).
 */
public record TrendHighlight(
        LocalDateTime at, // 그 순간의 시각
        String label,     // 이름
        Double value,     // 값
        String unit,      // 단위 (kW, %, ℃)
        String level      // CRITICAL(목표 초과) / INFO
) {
}
