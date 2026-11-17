package com.smartfactory.energy.raw;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전력원별 전력 원본 (solar_power 테이블). 시각 1개 × 종류 1개 = 1줄.
 */
@Entity
@Table(name = "solar_power")
@IdClass(SolarPowerId.class) // 기본키가 ts + powerType 두 칸
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolarPower {

    // 측정 시각
    @Id
    private LocalDateTime ts;

    // 종류: SOLAR / GRID / ESS / LOAD
    @Id
    @Column(length = 10)
    private String powerType;

    // 전력 kW
    @Column(columnDefinition = "numeric")
    private Double powerKw;
}
