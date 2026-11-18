package com.smartfactory.energy.global.failover;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Supabase(메인 DB)가 살아있는지 10초마다 확인해서 장애/복구를 감지하고,
 * FailoverRoutingDataSource가 쓸 "현재 대상(primary/backup)"을 들고 있습니다.
 *
 * 실제 서비스용 커넥션 풀(HikariCP)과 분리된 전용 연결로 확인합니다.
 * (죽은 DB에 풀 커넥션으로 계속 접속 시도하면 풀이 막혀버릴 수 있어서, DriverManager로 매번 새로 접속합니다)
 */
@Slf4j
@Component
public class SupabaseHealthChecker {

    private static final int FAIL_THRESHOLD = 3;
    private static final int SUCCESS_THRESHOLD = 3;
    private static final Duration MIN_HOLD = Duration.ofMinutes(5);

    private final String primaryUrl;
    private final String primaryUsername;
    private final String primaryPassword;
    private final DbSyncService dbSyncService;

    private volatile String status = "UP";
    private volatile String target = "primary";
    private volatile LocalDateTime lastChanged = LocalDateTime.now();
    private final AtomicReference<String> manualTarget = new AtomicReference<>();

    private int failCount = 0;
    private int successCount = 0;

    public SupabaseHealthChecker(
            @Value("${PRIMARY_DB_URL}") String primaryUrl,
            @Value("${PRIMARY_DB_USERNAME}") String primaryUsername,
            @Value("${PRIMARY_DB_PASSWORD}") String primaryPassword,
            DbSyncService dbSyncService) {
        this.primaryUrl = primaryUrl;
        this.primaryUsername = primaryUsername;
        this.primaryPassword = primaryPassword;
        this.dbSyncService = dbSyncService;
    }

    // FailoverRoutingDataSource가 쿼리마다 묻는 값
    public String currentTarget() {
        String manual = manualTarget.get();
        return manual != null ? manual : target;
    }

    public String status() {
        return status;
    }

    public LocalDateTime lastChanged() {
        return lastChanged;
    }

    // 수동 고정. null을 넣으면 다시 자동 판단으로 돌아갑니다. (DbStatusController가 호출)
    public void setManualTarget(String target) {
        manualTarget.set(target);
    }

    @Scheduled(fixedDelay = 10_000)
    public void check() {
        if (manualTarget.get() != null) {
            return;
        }

        if (ping()) {
            successCount++;
            failCount = 0;
        } else {
            failCount++;
            successCount = 0;
        }

        boolean held = Duration.between(lastChanged, LocalDateTime.now()).compareTo(MIN_HOLD) >= 0;

        if ("UP".equals(status) && failCount >= FAIL_THRESHOLD) {
            status = "DOWN";
            target = "backup";
            lastChanged = LocalDateTime.now();
            log.error("[DB-HEALTH] Supabase 장애 감지 — 백업 DB로 자동 전환");
            dbSyncService.onFailover();
        } else if ("DOWN".equals(status) && successCount >= SUCCESS_THRESHOLD && held) {
            log.warn("[DB-HEALTH] Supabase 복구 감지 — 데이터 동기화 후 자동 복귀 시도");
            if (dbSyncService.syncBackupToPrimary(lastChanged)) {
                status = "UP";
                target = "primary";
                lastChanged = LocalDateTime.now();
                log.warn("[DB-HEALTH] Failback 완료 — Supabase로 복귀");
            } else {
                log.warn("[DB-HEALTH] 동기화 실패 — 백업 DB 유지, 다음 주기에 재시도");
            }
        }
    }

    private boolean ping() {
        try {
            DriverManager.setLoginTimeout(3);
            try (Connection connection = DriverManager.getConnection(primaryUrl, primaryUsername, primaryPassword);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(3);
                statement.execute("SELECT 1");
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
