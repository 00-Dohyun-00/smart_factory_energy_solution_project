package com.smartfactory.energy.power;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 1시간 단위로 SQL이 더한 전력 합계 (성적표의 요금·ESS·태양광 계산용).
// 요금 시간대가 항상 정시(0,8,11,...)에 바뀌므로, 분 단위 대신 시간 단위로 묶어도 요금 계산이 똑같이 나옴
public interface HourlyPowerTotals {
    LocalDateTime getBucket();

    Long getRowCount();

    // sum(max(grid_kw, 0)) — 태양광이 남아 역송처럼 음수가 된 분은 구매가 없는 것으로 봄
    BigDecimal getGridKwSum();

    BigDecimal getSolarKwSum();

    // sum(max(ess_kw, 0))
    BigDecimal getDischargeKwSum();

    // sum(max(-ess_kw, 0))
    BigDecimal getChargeKwSum();

    // sum(min(solar_kw, load_kw)) — 공장이 태양광을 그 자리에서 쓴 양
    BigDecimal getMinSolarLoadKwSum();
}
