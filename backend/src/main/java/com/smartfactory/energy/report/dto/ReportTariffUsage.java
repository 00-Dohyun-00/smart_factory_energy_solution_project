package com.smartfactory.energy.report.dto;

/**
 * 요금 시간대 하나의 사용량과 요금.
 */
public record ReportTariffUsage(
        String period,   // LIGHT / MID / PEAK
        String label,    // 경부하 / 중간부하 / 최대부하
        Double kwh,      // 한전에서 산 전력량
        Double percent,  // 전체 중 비율 %
        Long charge      // 전력량요금 (원)
) {
}
