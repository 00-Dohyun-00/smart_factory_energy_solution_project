package com.smartfactory.energy.power;

import java.math.BigDecimal;

// 시각(0~23)별 평균 한전 구매 (여러 날짜를 하나의 시각으로 SQL이 묶어서 평균)
public interface HourlyGridAverage {
    Integer getHour();

    BigDecimal getAvgGridKw();
}
