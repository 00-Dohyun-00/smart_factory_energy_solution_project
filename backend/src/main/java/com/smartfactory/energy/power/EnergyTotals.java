package com.smartfactory.energy.power;

import java.math.BigDecimal;

// 기간 안 1분 기록의 grid/solar 합과 ESS 충·방전 합 (SQL SUM 결과 1행). 60으로 나누면 kWh
public record EnergyTotals(BigDecimal gridKwSum, BigDecimal solarKwSum, BigDecimal dischargeKwSum,
                            BigDecimal chargeKwSum) {
}
