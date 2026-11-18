package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공장 부하 원본 (raw_factory_load 테이블). 공공데이터 CSV를 넣어 두고 시뮬레이터가 읽습니다.
 */
@Entity
@Table(name = "raw_factory_load")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawFactoryLoad {

    // 저장 시각 (시뮬레이터가 우리 시스템 기준 "지금"으로 옮긴 시각)
    @Id
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    // 원본 CSV에 적힌 시각
    @Column(name = "source_recorded_at", nullable = false)
    private LocalDateTime sourceRecordedAt;

    // 공장 부하 kW
    @Column(name = "factory_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal factoryKw;

    public RawFactoryLoad(LocalDateTime recordedAt, LocalDateTime sourceRecordedAt, BigDecimal factoryKw) {
        this.recordedAt = recordedAt;
        this.sourceRecordedAt = sourceRecordedAt;
        this.factoryKw = factoryKw;
    }
}
