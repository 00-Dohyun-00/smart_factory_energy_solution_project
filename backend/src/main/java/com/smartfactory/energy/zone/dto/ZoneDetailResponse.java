package com.smartfactory.energy.zone.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구역 한 곳의 최근 1시간 전력과 오늘 요약 (설비 모니터링 화면의 오른쪽 상세).
 */
public record ZoneDetailResponse(
        String zoneId,            // 구역 코드 (예: Z2)
        List<ZonePoint> points,   // 최근 1시간, 1분 간격
        Double todayMaxKw,        // 오늘 최대 (기록 없으면 null)
        Double todayAvgKw,        // 오늘 평균 (기록 없으면 null)
        Double todayKwh           // 오늘 사용량
) {

    // 1분 1칸: 그 구역 설비 전력의 합
    public record ZonePoint(LocalDateTime recordedAt, Double loadKw) {
    }
}
