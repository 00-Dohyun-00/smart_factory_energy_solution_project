package com.smartfactory.energy.loadforecast.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 서버(FastAPI) GET /load-forecasts 응답. 15분 칸마다 공장 부하 예측 kW.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiLoadForecastResponse(
        String model,     // 예측 모델 이름
        List<Slot> slots
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slot(
            @JsonProperty("slot_start") LocalDateTime slotStart, // 칸 시작 시각
            @JsonProperty("load_kw") Double loadKw,              // 평균 예측 (요금 계산용)
            @JsonProperty("peak_load_kw") Double peakLoadKw      // 상위 90% 예측 (방어선 판단용)
    ) {
    }
}
