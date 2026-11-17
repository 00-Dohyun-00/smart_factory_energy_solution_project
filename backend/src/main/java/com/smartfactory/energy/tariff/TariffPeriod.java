package com.smartfactory.energy.tariff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전기요금 시간대 (tariff_period 테이블). 계절 × 시간대마다 단가가 1줄씩 있습니다.
 */
@Entity
@Table(name = "tariff_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TariffPeriod {

    // 번호 (자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long periodId;

    // 계절: SUMMER(여름) / SPRING_FALL(봄·가을) / WINTER(겨울)
    @Column(nullable = false, length = 12)
    private String season;

    // 시간대: LIGHT(경부하) / MID(중간부하) / PEAK(최대부하)
    @Column(nullable = false, length = 10)
    private String periodType;

    // 시작 시각 (예: 08:00)
    @Column(nullable = false)
    private LocalTime startTime;

    // 끝 시각 (예: 11:00)
    @Column(nullable = false)
    private LocalTime endTime;

    // 단가 원/kWh
    @Column(nullable = false, columnDefinition = "numeric(6,1)")
    private Double unitPrice;
}
