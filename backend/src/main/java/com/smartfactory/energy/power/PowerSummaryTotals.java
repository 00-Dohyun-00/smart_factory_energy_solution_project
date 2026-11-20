package com.smartfactory.energy.power;

import java.math.BigDecimal;

// 기간 요약(TrendSummary)에 쓰는 SQL 집계 1행. avg()는 JPA 규격상 항상 Double로 옴 (원래 컬럼이 BigDecimal이어도)
public record PowerSummaryTotals(Double avgGridKw, BigDecimal loadKwSum, BigDecimal solarKwSum,
                                  BigDecimal dischargeKwSum, BigDecimal chargeKwSum) {
}
