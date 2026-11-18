package com.smartfactory.energy.analysis.dto;

/**
 * 방전 추천을 처리 방법(적용·자동·넘김)별로 묶은 결과 1줄.
 */
public record TrendStatusResult(
        String status,           // APPLIED(적용) / AUTO(자동) / IGNORED(넘김)
        int count,               // 추천 수
        int defendedCount,       // 그중 목표 피크 이내로 막은 수
        Double defendedPercent,  // 막은 비율 %. 추천이 0건이면 null
        Double avgResultPeakKw   // 결과 피크 평균 kW. 추천이 0건이면 null
) {
}
