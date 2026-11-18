package com.smartfactory.energy.analysis.dto;

import java.time.LocalDate;

/**
 * 히트맵 칸 1개 = 날짜 1개 × 시각 1개의 최대 피크.
 */
public record PeakHeatmapCell(
        LocalDate date,
        int hour,       // 0~23
        Double peakKw   // 그 시간 안 15분 구간 중 최대 kW
) {
}
