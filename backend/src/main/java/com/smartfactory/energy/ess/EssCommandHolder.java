package com.smartfactory.energy.ess;

import org.springframework.stereotype.Component;

// 현재 ESS 지시값을 메모리에 들고 있습니다.
// 지금은 시뮬레이터만 읽고 항상 idle(대기)이고, 나중에 판단 엔진이 set()으로 충전/방전 값을 넣습니다.
@Component
public class EssCommandHolder {

    private volatile EssCommand current = EssCommand.idle();

    public EssCommand get() {
        return current;
    }

    public void set(EssCommand command) {
        this.current = command;
    }
}
