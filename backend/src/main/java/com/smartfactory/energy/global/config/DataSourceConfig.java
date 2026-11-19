package com.smartfactory.energy.global.config;

import com.smartfactory.energy.global.failover.FailoverRoutingDataSource;
import com.smartfactory.energy.global.failover.SupabaseHealthChecker;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * DB를 2개(메인 Supabase, 로컬 backup-db) 만들어두고, Spring이 실제로 쓰는 DataSource는
 * 그때그때 SupabaseHealthChecker가 정한 대상으로 쿼리를 보내는 FailoverRoutingDataSource 하나입니다.
 * 두 DB가 스키마(테이블 구조)가 같아서 Entity는 그대로 양쪽에 다 씁니다.
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.hikari.maximum-pool-size:3}")
    private int maximumPoolSize;

    @Bean
    public DataSource primaryDs(
            @Value("${PRIMARY_DB_URL}") String url,
            @Value("${PRIMARY_DB_USERNAME}") String username,
            @Value("${PRIMARY_DB_PASSWORD}") String password) {
        return buildDataSource(url, username, password);
    }

    @Bean
    public DataSource backupDs(
            @Value("${BACKUP_DB_URL}") String url,
            @Value("${BACKUP_DB_USERNAME}") String username,
            @Value("${BACKUP_DB_PASSWORD}") String password) {
        return buildDataSource(url, username, password);
    }

    // Spring이 실제로 주입받는 DataSource. 쿼리마다 checker가 정한 대상(primary/backup)으로 라우팅됩니다.
    @Bean
    @Primary
    public DataSource dataSource(
            SupabaseHealthChecker checker,
            @Qualifier("primaryDs") DataSource primaryDs,
            @Qualifier("backupDs") DataSource backupDs) {
        FailoverRoutingDataSource routing = new FailoverRoutingDataSource(checker);
        routing.setTargetDataSources(Map.of("primary", primaryDs, "backup", backupDs));
        routing.setDefaultTargetDataSource(primaryDs);
        routing.afterPropertiesSet();
        return routing;
    }

    private DataSource buildDataSource(String url, String username, String password) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setMaximumPoolSize(maximumPoolSize);
        // 둘 중 하나가 기동 시점에 이미 장애 상태여도 서버가 뜰 수 있게,
        // 풀 생성 시 첫 연결 확인에서 실패해도 앱을 멈추지 않습니다. (연결은 실제로 쓸 때 다시 시도)
        dataSource.setInitializationFailTimeout(-1);
        // 전환된 DB가 실제로는 연결이 안 될 때, 기본값(30초)만큼 요청이 멈춰있지 않고 빠르게 실패하도록
        dataSource.setConnectionTimeout(5_000);
        return dataSource;
    }
}
