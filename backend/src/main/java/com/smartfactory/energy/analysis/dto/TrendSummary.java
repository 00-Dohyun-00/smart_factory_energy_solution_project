package com.smartfactory.energy.analysis.dto;

import java.time.LocalDateTime;

/**
 * 추이 분석 화면 위쪽의 요약 숫자 6칸.
 * 값을 구할 기록이 없는 칸은 null.
 */
public record TrendSummary(
        Double maxPeakKw,           // 기간 중 가장 높았던 15분 평균 한전 구매 kW (= 피크)
        LocalDateTime maxPeakAt,    // 그 15분 구간의 시작 시각
        Double avgGridKw,           // 기간 평균 한전 구매 kW
        Double solarKwh,            // 태양광 발전량 kWh
        Double solarPercentOfLoad,  // 공장 소비 중 태양광 비율 %
        Double dischargeKwh,        // ESS 방전량 kWh
        Double chargeKwh,           // ESS 충전량 kWh
        Double efficiencyPercent,   // 충·방전 효율 % (방전량 ÷ 충전량)
        Double socMinPercent,       // 배터리 잔량 최저 %
        LocalDateTime socMinAt,     // 잔량이 최저였던 시각
        Double socMaxPercent,       // 배터리 잔량 최고 %
        Double roomTempMaxC,        // 배터리실 최고 온도 ℃
        LocalDateTime roomTempMaxAt // 최고 온도였던 시각
) {
}
