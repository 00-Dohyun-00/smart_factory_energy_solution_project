package com.smartfactory.energy.ess;

import java.math.BigDecimal;

// 기간의 평균·최저 잔량 (SQL AVG/MIN 1행). 기록이 없으면 둘 다 null
// avg()는 JPA 규격상 항상 Double로 옴 (원래 컬럼이 BigDecimal이어도), min()은 원래 타입(BigDecimal) 그대로 옴
public record EssSocTotals(Double avgSoc, BigDecimal minSoc) {
}
