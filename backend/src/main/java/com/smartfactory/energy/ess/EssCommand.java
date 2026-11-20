package com.smartfactory.energy.ess;

import java.math.BigDecimal;

// ESS에게 내리는 지시값. RecommendationService.currentCommand()가 recommendation 테이블에서
// "지금 유효한 적용된 추천"을 보고 만들어 줍니다.
public record EssCommand(EssStatus status, BigDecimal powerKw) {

    public static EssCommand idle() {
        return new EssCommand(EssStatus.IDLE, BigDecimal.ZERO);
    }
}
