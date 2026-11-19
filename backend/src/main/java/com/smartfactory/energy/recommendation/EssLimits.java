package com.smartfactory.energy.recommendation;

/**
 * 판단 엔진이 쓰는 환경 설정 값 묶음 (setting 테이블에서 읽음).
 * warnKw(주의선) = 목표 피크 × peak.warn_ratio ÷ 100 (예: 1,800 × 97% = 1,746 kW)
 */
public record EssLimits(
        double targetPeakKw,      // 목표 피크 kW
        double warnKw,            // 주의선 kW
        double maxPowerKw,        // 최대 충·방전 출력 kW
        double capacityKwh,       // 배터리 용량 kWh
        double socLowPercent,     // 잔량 하한 %
        double socHighPercent,    // 잔량 상한 %
        double efficiencyPercent  // 충·방전 효율 %
) {

    // ESS 전력(+ 방전, − 충전)을 15분 동안 냈을 때 바뀌는 잔량 (%). 충전은 손실(효율)만큼 덜 참
    public double socChangeOf(double essKw) {
        if (essKw > 0) {
            return -(essKw * 0.25 / capacityKwh) * 100;
        }
        return (-essKw * 0.25 * efficiencyPercent / 100 / capacityKwh) * 100;
    }
}
