package com.smartfactory.energy.simulator;

import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// ESS 배터리 잔량(SOC, %)을 메모리에 들고 있다가 매초 갱신합니다.
// 서버를 다시 켜면 ess_status_minute의 마지막 soc에서 이어서 시작하고, 기록이 없으면 50%에서 시작합니다.
@Component
@RequiredArgsConstructor
public class EssSocState {

    private static final BigDecimal DEFAULT_SOC = BigDecimal.valueOf(50);

    private final EssStatusMinuteRepository essStatusMinuteRepository;

    private BigDecimal soc;

    public BigDecimal get() {
        if (soc == null) {
            soc = essStatusMinuteRepository.findFirstByOrderByTsDesc()
                    .map(EssStatusMinute::getSoc)
                    .orElse(DEFAULT_SOC);
        }
        return soc;
    }

    public void set(BigDecimal soc) {
        this.soc = soc;
    }
}
