package com.smartfactory.energy.power;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// interval(QUARTER/HOUR/DAY) 단위로 SQL이 평균 낸 1묶음 (추이 분석 그래프 점)
public interface PowerBucketAverage {
    LocalDateTime getBucket();

    BigDecimal getAvgLoadKw();

    BigDecimal getAvgGridKw();

    BigDecimal getAvgSolarKw();

    BigDecimal getAvgEssKw();
}
