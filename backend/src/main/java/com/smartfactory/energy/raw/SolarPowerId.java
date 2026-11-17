package com.smartfactory.energy.raw;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * solar_power 테이블의 기본키 (시각 + 전력 종류, 두 칸을 합쳐서 기본키).
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SolarPowerId implements Serializable {

    private LocalDateTime ts;
    private String powerType;
}
