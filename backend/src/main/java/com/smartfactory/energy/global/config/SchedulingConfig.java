package com.smartfactory.energy.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled(주기 실행) 설정.
 *
 * Supabase 장애 감지(SupabaseHealthChecker)와 백업 DB 동기화(DbSyncService)는
 * 모든 PC에서 항상 돌아야 해서 @EnableScheduling은 항상 켜 둡니다.
 *
 * 시뮬레이터(PowerSimulator, MinuteAggregationJob)는 팀 공용 DB라서 한 명만 돌려야 하므로,
 * 이 설정과는 별개로 각 클래스에 @ConditionalOnProperty(simulator.enabled=true)를 개별로 붙여뒀습니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
