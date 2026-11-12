package com.smartfactory.energy.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled(주기 실행) 설정.
 *
 * 팀 공용 DB(Supabase)를 쓰기 때문에 시뮬레이터 같은 주기 실행 작업은 한 명의 PC에서만 돌아야 합니다.
 * (여러 명이 동시에 실행하면 같은 테이블에 데이터가 중복으로 쌓입니다.)
 *
 * backend/.env 에 SIMULATOR_ENABLED=true 를 넣은 사람만 @Scheduled 작업이 실행됩니다. (기본값: 꺼짐)
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
public class SchedulingConfig {

    public SchedulingConfig() {
        log.info("시뮬레이터(@Scheduled 작업) 켜짐: 이 PC에서 주기 실행 작업이 동작합니다.");
    }
}
