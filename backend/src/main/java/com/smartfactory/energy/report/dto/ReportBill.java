package com.smartfactory.energy.report.dto;

import java.util.List;

/**
 * 전기요금 추정 (원 단위 정수).
 */
public record ReportBill(
        Long total,                     // 합계 = 전력량요금 + 기본요금
        Long energyCharge,              // 전력량요금 = 시간대별 kWh × 단가
        Long baseCharge,                // 기본요금 = 이번 달 최대 피크 × 기본요금 단가
        Long baseChargeOverTarget,      // 목표 피크를 넘어서 더 낸 기본요금
        Long monthEndEstimate,          // 월말 예상 요금
        Long lastMonthTotal,            // 전달 요금 (기록 없으면 null)
        List<ReportTariffUsage> byTariff // 요금 시간대별 사용량과 요금
) {
}
