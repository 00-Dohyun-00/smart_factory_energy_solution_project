package com.smartfactory.energy.analysis.dto;

import java.time.LocalDateTime;

/**
 * 피크가 높았던 15분 구간 1개와 그때의 방전 추천 처리.
 */
public record PeakWindow(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Double peakKw,
        String status,  // APPLIED / AUTO / IGNORED / RECOMMENDED. 방전 추천이 없었으면 null
        String note     // 추천 설명 문장. 추천이 없었으면 "방전 추천 없음"
) {
}
