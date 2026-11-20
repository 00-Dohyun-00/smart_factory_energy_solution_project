package com.smartfactory.energy.simulator;

import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ESS 배터리 잔량(SOC, %)을 메모리에 들고 있다가 매초 갱신합니다.
 * 서버 시작 때 한 번만 ess_status_minute의 마지막 soc에서 이어받고(기록이 없으면 50%에서 시작),
 * 그 뒤로는 DB를 다시 읽지 않습니다.
 *
 * 매초 바뀌는 양을 소수 2자리로 반올림해서 저장하면, 예를 들어 500kW 방전도 1초로는 0.0139%p밖에
 * 안 바뀌어서 반올림 때문에 잔량이 거의 안 움직이는 것처럼 보일 수 있습니다. 그래서 메모리에는 반올림
 * 없는 값(double)을 그대로 쌓아 두고, 화면·DB에 보여줄 때만(get()) 소수 2자리로 반올림합니다.
 */
@Component
@RequiredArgsConstructor
public class EssSocState {

    private static final double DEFAULT_SOC = 50;

    private final EssStatusMinuteRepository essStatusMinuteRepository;

    // 반올림 없이 누적되는 값. 서버가 막 떠서 아직 못 읽었으면 NaN
    private volatile double soc = Double.NaN;

    // 서버 시작 때 한 번만 DB의 마지막 값에서 이어받음
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        soc = loadFromDb();
    }

    // 반올림 없는 지금 값 (매초 계산에 씀)
    public double getRaw() {
        if (Double.isNaN(soc)) {
            soc = loadFromDb(); // 드물게 서버 시작 이벤트보다 먼저 불릴 때를 대비
        }
        return soc;
    }

    // 화면·DB 저장용 (소수 2자리로 반올림)
    public BigDecimal get() {
        return BigDecimal.valueOf(getRaw()).setScale(2, RoundingMode.HALF_UP);
    }

    // 반올림 없이 그대로 저장 (매초 PowerSimulator가 계산한 값)
    public void set(double soc) {
        this.soc = soc;
    }

    private double loadFromDb() {
        return essStatusMinuteRepository.findFirstByOrderByTsDesc()
                .map(e -> e.getSoc().doubleValue())
                .orElse(DEFAULT_SOC);
    }
}
