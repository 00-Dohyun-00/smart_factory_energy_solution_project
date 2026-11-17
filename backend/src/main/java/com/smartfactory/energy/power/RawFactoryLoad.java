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

// raw_factory_load 테이블 (공공데이터 CSV를 적재한 원본 테이블. 시뮬레이터가 읽어서 씀)
@Entity
@Table(name = "raw_factory_load")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawFactoryLoad {

    @Id
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(name = "source_recorded_at", nullable = false)
    private LocalDateTime sourceRecordedAt;

    @Column(name = "factory_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal factoryKw;

    public RawFactoryLoad(LocalDateTime recordedAt, LocalDateTime sourceRecordedAt, BigDecimal factoryKw) {
        this.recordedAt = recordedAt;
        this.sourceRecordedAt = sourceRecordedAt;
        this.factoryKw = factoryKw;
    }
}
