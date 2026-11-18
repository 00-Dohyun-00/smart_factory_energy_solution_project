package com.smartfactory.energy.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 목표 피크를 넘은 15분 구간 1개. 그 구간의 추천이 없으면 action, powerKw, status는 null.
 */
public record ReportExceeded(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Double resultPeakKw,  // 실제 피크
        Double overKw,        // 목표를 넘은 양
        String action,        // CHARGE / DISCHARGE / IDLE
        BigDecimal powerKw,   // 추천 출력
        String status,        // RECOMMENDED / APPLIED / AUTO / IGNORED
        String cause          // 추천 설명 문장
) {
}
