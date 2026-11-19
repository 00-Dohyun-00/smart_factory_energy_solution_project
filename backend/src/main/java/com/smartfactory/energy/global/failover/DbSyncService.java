package com.smartfactory.energy.global.failover;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Supabase(primary)와 로컬 backup-db 사이의 데이터 동기화.
 * - 평소(5분마다): primary -> backup 증분 복사 (Supabase가 정상일 때만)
 * - Failback 때: backup -> primary로 장애 구간 복사
 *
 * 1초 원본(solar_power)은 Node-RED가 평소에 두 DB에 직접 이중 저장하므로,
 * 여기서는 복사하지 않습니다. Failback 때 "장애 중 backup에만 쌓인 구간"만 메워줍니다.
 */
@Slf4j
@Service
public class DbSyncService {

    // 복사 대상: 테이블 이름, 시각 컬럼, 중복 방지용 키 컬럼(들)
    private record SyncTable(String table, String tsColumn, String conflictColumns) {
    }

    private static final List<SyncTable> ALWAYS_SYNCED_TABLES = List.of(
            new SyncTable("recommendation", "recommended_at", "recommendation_id"),
            new SyncTable("event_log", "occurred_at", "event_id"),
            new SyncTable("site_power_minute", "ts", "ts"),
            new SyncTable("ess_status_minute", "ts", "ts"),
            new SyncTable("power_minute", "ts", "equipment_id, ts")
    );

    // Failback 때만: 장애 중 Node-RED가 backup에만 넣은 실측 구간
    private static final SyncTable SOLAR_POWER_TABLE = new SyncTable("solar_power", "ts", "ts, power_type");

    private final JdbcTemplate primaryJdbc;
    private final JdbcTemplate backupJdbc;

    private volatile boolean usingBackup = false;
    private volatile LocalDateTime lastIncrementalSync = LocalDateTime.now();

    public DbSyncService(@Qualifier("primaryDs") DataSource primaryDs, @Qualifier("backupDs") DataSource backupDs) {
        this.primaryJdbc = new JdbcTemplate(primaryDs);
        this.backupJdbc = new JdbcTemplate(backupDs);
    }

    // Failover 시작 시 SupabaseHealthChecker가 호출. 전환 중엔 증분 복사를 멈춥니다.
    public void onFailover() {
        usingBackup = true;
    }

    // 평소 5분마다: Supabase -> 백업 DB로 최근 데이터 증분 복사
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void syncPrimaryToBackup() {
        if (usingBackup) {
            return;
        }
        LocalDateTime since = lastIncrementalSync;
        try {
            for (SyncTable table : ALWAYS_SYNCED_TABLES) {
                copyRows(primaryJdbc, backupJdbc, table, since);
            }
            lastIncrementalSync = LocalDateTime.now();
        } catch (Exception e) {
            log.warn("[DB-SYNC] 증분 복사(primary->backup) 실패, 다음 주기에 재시도: {}", e.getMessage());
        }
    }

    // Failback: 장애 구간(since ~ 지금)을 백업 DB -> Supabase로 복사. 성공하면 true.
    public boolean syncBackupToPrimary(LocalDateTime since) {
        try {
            for (SyncTable table : ALWAYS_SYNCED_TABLES) {
                copyRows(backupJdbc, primaryJdbc, table, since);
            }
            copyRows(backupJdbc, primaryJdbc, SOLAR_POWER_TABLE, since);
            usingBackup = false;
            lastIncrementalSync = LocalDateTime.now();
            return true;
        } catch (Exception e) {
            log.warn("[DB-SYNC] Failback 동기화(backup->primary) 실패: {}", e.getMessage());
            return false;
        }
    }

    // from의 since 이후 행을 to로 그대로 복사합니다. 컬럼을 하나하나 적지 않고 전체를 그대로 옮깁니다.
    private void copyRows(JdbcTemplate from, JdbcTemplate to, SyncTable table, LocalDateTime since) {
        String selectSql = "select * from " + table.table() + " where " + table.tsColumn() + " >= ?";
        from.query(selectSql, (ResultSet rs) -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }
            String insertSql = "insert into " + table.table() + " (" + String.join(", ", columnNames) + ") "
                    + "values (" + String.join(", ", Collections.nCopies(columnCount, "?")) + ") "
                    + "on conflict (" + table.conflictColumns() + ") do nothing";
            while (rs.next()) {
                Object[] values = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    values[i - 1] = rs.getObject(i);
                }
                to.update(insertSql, values);
            }
            return null;
        }, Timestamp.valueOf(since));
    }
}
