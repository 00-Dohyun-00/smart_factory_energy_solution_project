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

// tariff_period 테이블 (계절/시간대별 전기 요금 기준 데이터)
@Entity
@Table(name = "tariff_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TariffPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    private Long periodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TariffSeason season;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private TariffPeriodType periodType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

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
