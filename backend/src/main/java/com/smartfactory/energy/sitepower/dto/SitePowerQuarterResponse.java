package com.smartfactory.energy.sitepower.dto;

import java.time.LocalDateTime;

/**
 * 오늘 15분 구간 1칸 (모니터링 > 통합의 오늘 그래프).
 */
public record SitePowerQuarterResponse(
        LocalDateTime recordedAt, // 15분 구간 시작
        Double loadKw,            // 구간 평균 공장 전력
        Double solarKw,           // 구간 평균 태양광
        Double gridKw,            // 구간 평균 한전 구매
        Double socPercent         // 구간 마지막 1분의 배터리 잔량 (기록 없으면 null)
) {
}
