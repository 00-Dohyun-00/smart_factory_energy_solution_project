package com.smartfactory.energy.tariff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    private Long periodId;

    // 계절: SUMMER(여름) / SPRING_FALL(봄・가을) / WINTER(겨울)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TariffSeason season;

    // 시간대: OFF_PEAK(경부하) / MID_PEAK(중간부하) / ON_PEAK(최대부하)
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private TariffPeriodType periodType;

    // 시작 시각 (예: 08:00)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    // 끝 시각 (예: 11:00)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // 단가 원/kWh
    @Column(name = "unit_price", nullable = false, precision = 6, scale = 1)
    private BigDecimal unitPrice;

    public TariffPeriod(TariffSeason season, TariffPeriodType periodType, LocalTime startTime, LocalTime endTime,
                         BigDecimal unitPrice) {
        this.season = season;
        this.periodType = periodType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.unitPrice = unitPrice;
    }
}
