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

// solar_power 테이블 (태양광 발전/계통 전력 원본 데이터)
@Entity
@Table(name = "solar_power")
@IdClass(SolarPowerId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolarPower {

    @Id
    private LocalDateTime ts;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "power_type", length = 10)
    private SolarPowerType powerType;

    @Column(name = "power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal powerKw;

    public SolarPower(LocalDateTime ts, SolarPowerType powerType, BigDecimal powerKw) {
        this.ts = ts;
        this.powerType = powerType;
        this.powerKw = powerKw;
    }
}
