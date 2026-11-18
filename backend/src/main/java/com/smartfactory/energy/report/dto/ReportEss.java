package com.smartfactory.energy.report.dto;

import java.util.List;

/**
 * ESS 운용 결과.
 */
public record ReportEss(
        List<ReportDailyDischarge> dailyDischarge, // 날짜별 방전량
        Double dischargeKwh,       // 방전 합계
        Double chargeKwh,          // 충전 합계
        Double efficiencyPercent,  // 충방전 효율 = 방전 ÷ 충전 × 100
        Double cycles,             // 충방전 횟수 = 방전량 ÷ 배터리 용량
        Double avgSocPercent,      // 평균 잔량
        Double minSocPercent,      // 최저 잔량
        int blockedCount           // 적용했는데도 목표를 넘은 횟수
) {
}
