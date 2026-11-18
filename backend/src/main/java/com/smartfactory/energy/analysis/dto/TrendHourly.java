package com.smartfactory.energy.analysis.dto;

/**
 * 시각(0~23시)별 평균 한전 구매와 그 시각의 요금 시간대.
 */
public record TrendHourly(
        int hour,          // 0~23
        Double avgGridKw,  // 그 시각의 평균 한전 구매 kW. 기록 없으면 null
        String period      // 요금 시간대: LIGHT(경부하) / MID(중간) / PEAK(최대). 요금표 없으면 null
) {
}
