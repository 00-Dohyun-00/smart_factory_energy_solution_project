package com.smartfactory.energy.ess;

import java.math.BigDecimal;

// ESS에게 내리는 지시값 (판단 엔진이 나중에 EssCommandHolder.set()으로 채워 넣음)
public record EssCommand(EssStatus status, BigDecimal powerKw) {

    public static EssCommand idle() {
        return new EssCommand(EssStatus.IDLE, BigDecimal.ZERO);
    }
}
