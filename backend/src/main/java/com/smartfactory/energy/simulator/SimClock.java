package com.smartfactory.energy.simulator;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

// 시뮬레이션에서 쓰는 "지금" 시각. 모든 시각은 이 클래스를 통해서만 얻습니다.
// 지금은 실제 현재 시각을 그대로 돌려주고, 나중에 배속(speed-up)을 추가할 때 이 안에서만 계산하면 됩니다.
@Component
public class SimClock {

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
