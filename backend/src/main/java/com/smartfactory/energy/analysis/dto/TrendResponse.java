package com.smartfactory.energy.analysis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 추이 분석 응답 전체. 화면 구역마다 칸 하나씩.
 */
public record TrendResponse(
        LocalDate from,
        LocalDate to,
        String interval,
        Double targetPeakKw,                // 그래프 기준선: 목표 피크 (setting: peak.target_kw)
        Double socLowLimit,                 // 그래프 기준선: 잔량 하한 (setting: ess.soc_low)
        Double socHighLimit,                // 그래프 기준선: 잔량 상한 (setting: ess.soc_high)
        Double roomTempLimitC,              // 그래프 기준선: 배터리실 온도 한계 (setting: ess.room_temp_limit)
        TrendSummary summary,               // 위쪽 요약 숫자. 기록이 하나도 없으면 null
        List<TrendPoint> points,            // 추이 그래프 점
        List<TrendWeather> weather,         // 날짜별 날씨
        List<TrendHourly> hourly,           // 시각별 평균 한전 구매
        List<TrendStatusResult> byStatus,   // 추천 처리별 결과
        List<TrendHighlight> highlights     // 눈에 띄는 구간
) {
}
