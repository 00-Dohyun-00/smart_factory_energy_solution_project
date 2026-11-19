package com.smartfactory.energy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 1분마다 판단 엔진을 돌림. 시뮬레이터가 매분 0초에 1분 기록을 저장하고, 피크 이벤트가 10초에 도니 20초에 실행
// 팀 공용 DB라서 시뮬레이터를 켠 PC(SIMULATOR_ENABLED=true)에서만 돌림 (추천이 두 번 쌓이지 않게)
// 지금 잔량(EssSocState)과 예상 태양광(SolarForecastService)도 그 PC 메모리에만 있음
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RecommendationEngineJob {

    private final RecommendationEngine recommendationEngine;

    @Scheduled(cron = "20 * * * * *")
    public void run() {
        recommendationEngine.run();
    }
}
