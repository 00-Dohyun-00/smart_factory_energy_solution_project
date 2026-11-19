package com.smartfactory.energy.weather.dto;

import java.time.LocalDateTime;

/**
 * 앞으로의 15분 칸 1개의 예상. 판단 엔진·하루 추천이 이 목록을 받아 씁니다.
 * 예보가 없는 칸은 일사량·기온·운량이 null이고 발전량은 0입니다. (피크 방어에는 태양광을 적게 보는 쪽이 안전)
 */
public record SolarForecastSlot(
        LocalDateTime slotStart, // 칸 시작 시각 (예: 14:45 → 14:45~15:00 칸)
        Double radiationWm2,     // 이 칸의 평균 일사량 W/m²
        double solarKw,          // 예상 태양광 발전량 kW = 일사량 × 용량 × 성능비 ÷ 1000
        Double temperatureC,     // 칸이 끝나는 시각의 기온 ℃
        Double cloudCover        // 칸이 끝나는 시각의 운량 %
) {
}
