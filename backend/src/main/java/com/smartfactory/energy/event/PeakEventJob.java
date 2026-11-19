package com.smartfactory.energy.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 1분마다 피크 이벤트를 남김. 시뮬레이터가 매분 0초에 1분 기록을 저장하므로 10초 뒤에 실행함
// 팀 공용 DB라서 시뮬레이터를 켠 PC(SIMULATOR_ENABLED=true)에서만 돌림 (이벤트가 두 번 쌓이지 않게)
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PeakEventJob {

    private final PeakEventService peakEventService;

    @Scheduled(cron = "10 * * * * *")
    public void run() {
        peakEventService.record();
    }
}
