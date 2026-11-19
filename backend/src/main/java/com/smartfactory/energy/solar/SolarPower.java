package com.smartfactory.energy.solar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전력원별 전력 원본 (solar_power 테이블). 시각 1개 × 종류 1개 = 1줄.
 */
@Entity
@Table(name = "solar_power")
@IdClass(SolarPowerId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolarPower {

    @Id
    private LocalDateTime ts;

    // 종류: SOLAR / GRID / ESS / LOAD
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "power_type", length = 10)
    private SolarPowerType powerType;

    // DB는 NULL을 허용함 (아직 값을 못 받은 시각이 있을 수 있음)
    @Column(name = "power_kw", precision = 10, scale = 2)
    private BigDecimal powerKw;

    public SolarPower(LocalDateTime ts, SolarPowerType powerType, BigDecimal powerKw) {
        this.ts = ts;
        this.powerType = powerType;
        this.powerKw = powerKw;
    }
}
