package com.smartfactory.energy.weather;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날씨 (weather 테이블). 기상 자료를 받아 시각별로 저장합니다.
 */
@Entity
@Table(name = "weather")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather {

    // 기준 시각
    @Id
    private LocalDateTime ts;

    // 기온 ℃
    @Column(columnDefinition = "numeric")
    private Double temperature;

    // 습도 %
    @Column(columnDefinition = "numeric")
    private Double humidity;

    // 운량 % (구름이 하늘을 덮은 정도)
    @Column(columnDefinition = "numeric")
    private Double cloudCover;

    // 하늘 상태: 맑음 / 구름많음 / 흐림
    @Column(length = 10)
    private String sky;

    // 일사량 W/m²
    @Column(name = "radiation_wm2", columnDefinition = "numeric")
    private Double radiationWm2;

    // 강수량 mm
    @Column(columnDefinition = "numeric")
    private Double precipitationMm;

    // 자료 출처: ASOS(기상청 관측) / OPEN_METEO
    @Column(length = 12)
    private String source;
}
