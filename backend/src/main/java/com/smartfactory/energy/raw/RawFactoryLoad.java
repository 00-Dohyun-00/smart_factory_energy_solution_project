package com.smartfactory.energy.raw;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    // 저장 시각 (우리 시스템 기준으로 옮긴 시각)
    @Id
    private LocalDateTime recordedAt;

    // 원본 CSV에 적힌 시각
    private LocalDateTime sourceRecordedAt;

    // 공장 부하 kW
    @Column(columnDefinition = "numeric")
    private Double factoryKw;
}
