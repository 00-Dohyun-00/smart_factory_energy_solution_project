package com.smartfactory.energy.ess;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

// 현재 ESS 지시값을 메모리에 들고 있습니다.
// 추천을 적용하면 set()으로 값이 들어오고, validUntil 시각이 지나면 get()에서 자동으로 대기(idle)로 돌아갑니다.
// (validUntil은 추천 적용 시각 기준의 실제 시각이라 SimClock이 아닌 실제 현재 시각으로 비교합니다)
@Component
public class EssCommandHolder {

    private volatile EssCommand current = EssCommand.idle();
    private volatile LocalDateTime validUntil;

    public EssCommand get() {
        if (validUntil != null && !LocalDateTime.now().isBefore(validUntil)) {
            current = EssCommand.idle();
            validUntil = null;
        }
        return current;
    }

    // command를 validUntil 시각까지 유지합니다. 그 전에 새 추천이 적용되면 덮어씁니다.
    public void set(EssCommand command, LocalDateTime validUntil) {
        this.current = command;
        this.validUntil = validUntil;
    }
}
