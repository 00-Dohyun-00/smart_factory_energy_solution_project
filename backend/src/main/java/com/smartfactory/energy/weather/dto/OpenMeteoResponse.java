package com.smartfactory.energy.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Open-Meteo 예보 API 응답 중 우리가 쓰는 칸만 받습니다. (나머지 칸은 무시)
 * timezone=Asia/Seoul로 요청해서 시각은 한국 시각("2026-11-19T14:45")으로 옵니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponse(
        Current current,
        @JsonProperty("minutely_15") Minutely15 minutely15
) {

    // 지금 15분 칸의 값. time은 지금이 속한 15분 칸 (예: 14:51에 부르면 14:45)
    // 관측값이 아니라 예보 모델 값입니다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
            LocalDateTime time,
            @JsonProperty("temperature_2m") Double temperature,      // 기온 ℃
            @JsonProperty("relative_humidity_2m") Double humidity,   // 습도 %
            @JsonProperty("cloud_cover") Double cloudCover,          // 운량 %
            @JsonProperty("shortwave_radiation") Double radiationWm2, // 일사량 W/m² (직전 15분 평균)
            Double precipitation                                     // 강수량 mm
    ) {
    }

    // 15분 간격 예보. 목록마다 같은 번째 값끼리 한 칸입니다.
    // 일사량은 그 시각 "직전 15분"의 평균 (예: 15:00 값 = 14:45~15:00 평균)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Minutely15(
            List<LocalDateTime> time,
            @JsonProperty("shortwave_radiation") List<Double> radiationWm2,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("cloud_cover") List<Double> cloudCover
    ) {
    }
}
