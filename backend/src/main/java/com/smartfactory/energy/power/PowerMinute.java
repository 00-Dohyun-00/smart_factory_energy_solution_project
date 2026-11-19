package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비별 1분 전력 (power_minute 테이블). 설비 1개 × 1분 = 1줄.
 */
@Entity
@Table(name = "power_minute")
@IdClass(PowerMinuteId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PowerMinute {

    @Id
    @Column(name = "equipment_id", length = 30)
    private String equipmentId;

    @Id
    private LocalDateTime ts;

    @Column(name = "avg_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgKw;

    // 0 또는 1 (가동 여부). DB는 smallint라서 Short로 매핑합니다.
    @Column(name = "is_running", nullable = false)
    private Short isRunning;

    public PowerMinute(String equipmentId, LocalDateTime ts, BigDecimal avgKw, Short isRunning) {
        this.equipmentId = equipmentId;
        this.ts = ts;
        this.avgKw = avgKw;
        this.isRunning = isRunning;
    }
}
