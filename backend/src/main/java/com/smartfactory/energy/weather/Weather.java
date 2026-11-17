package com.smartfactory.energy.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// weather 테이블 (기상 데이터. 날씨 수집 담당이 저장)
@Entity
@Table(name = "weather")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather {

    @Id
    private LocalDateTime ts;

    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(precision = 5, scale = 2)
    private BigDecimal humidity;

    @Column(name = "cloud_cover", precision = 5, scale = 2)
    private BigDecimal cloudCover;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sky sky;

    @Column(name = "radiation_wm2", precision = 7, scale = 2)
    private BigDecimal radiationWm2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WeatherSource source;

    @Column(name = "precipitation_mm", precision = 5, scale = 2)
    private BigDecimal precipitationMm;

    public Weather(LocalDateTime ts, BigDecimal temperature, BigDecimal humidity, BigDecimal cloudCover, Sky sky,
                   BigDecimal radiationWm2, WeatherSource source, BigDecimal precipitationMm) {
        this.ts = ts;
        this.temperature = temperature;
        this.humidity = humidity;
        this.cloudCover = cloudCover;
        this.sky = sky;
        this.radiationWm2 = radiationWm2;
        this.source = source;
        this.precipitationMm = precipitationMm;
    }
}
