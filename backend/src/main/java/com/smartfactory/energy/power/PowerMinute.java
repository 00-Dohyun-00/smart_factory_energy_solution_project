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

// power_minute 테이블 (설비별 1분 집계)
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

    @Column(name = "max_kw", precision = 10, scale = 2)
    private BigDecimal maxKw;

    @Column(name = "min_kw", precision = 10, scale = 2)
    private BigDecimal minKw;

    @Column(name = "energy_kwh", precision = 10, scale = 4)
    private BigDecimal energyKwh;

    @Column(name = "grid_kwh", precision = 10, scale = 4)
    private BigDecimal gridKwh;

    @Column(name = "solar_kwh", precision = 10, scale = 4)
    private BigDecimal solarKwh;

    @Column(name = "ess_kwh", precision = 10, scale = 4)
    private BigDecimal essKwh;

    // 0 또는 1 (가동 여부). DB는 smallint라서 Short로 매핑합니다.
    @Column(name = "is_running", nullable = false)
    private Short isRunning;

    public PowerMinute(String equipmentId, LocalDateTime ts, BigDecimal avgKw, BigDecimal maxKw, BigDecimal minKw,
                        BigDecimal energyKwh, BigDecimal gridKwh, BigDecimal solarKwh, BigDecimal essKwh,
                        Short isRunning) {
        this.equipmentId = equipmentId;
        this.ts = ts;
        this.avgKw = avgKw;
        this.maxKw = maxKw;
        this.minKw = minKw;
        this.energyKwh = energyKwh;
        this.gridKwh = gridKwh;
        this.solarKwh = solarKwh;
        this.essKwh = essKwh;
        this.isRunning = isRunning;
    }
}
