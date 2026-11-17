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

/**
 * 날씨 (weather 테이블). 기상 자료를 받아 시각별로 저장합니다. (날씨 수집 담당이 저장)
 */
@Entity
@Table(name = "weather")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather {

    @Id
    private LocalDateTime ts;

    // 기온 ℃
    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    // 습도 %
    @Column(precision = 5, scale = 2)
    private BigDecimal humidity;

    // 운량 % (구름이 하늘을 덮은 정도)
    @Column(name = "cloud_cover", precision = 5, scale = 2)
    private BigDecimal cloudCover;

    // 하늘 상태: CLEAR(맑음) / CLOUDY(구름많음) / OVERCAST(흐림)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sky sky;

    // 일사량 W/m²
    @Column(name = "radiation_wm2", precision = 7, scale = 2)
    private BigDecimal radiationWm2;

    // 자료 출처: ASOS(기상청 관측) / OPEN_METEO
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WeatherSource source;

    // 강수량 mm
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
