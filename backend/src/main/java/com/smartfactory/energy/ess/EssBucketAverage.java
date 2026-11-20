package com.smartfactory.energy.ess;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// interval(QUARTER/HOUR/DAY) 단위로 SQL이 평균 낸 ESS 상태 1묶음 (추이 분석 그래프 점)
public interface EssBucketAverage {
    LocalDateTime getBucket();

    BigDecimal getAvgSoc();

    BigDecimal getAvgBatteryTemp();
}
