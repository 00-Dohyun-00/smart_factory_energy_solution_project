package com.smartfactory.energy.loadforecast.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 판단 엔진에 넘기는 공장 부하 예측 (지금 15분 칸부터 칸마다).
 * method: 어떤 방법으로 예측했는지 (화면·이력에서 AI 예측인지 대신 쓴 예측인지 알 수 있게)
 */
public record LoadForecast(
        String method,
        boolean ai,        // AI 서버 예측이면 true, 대신 쓴 예측(최근 1시간 평균)이면 false
        double biasKw,     // AI 예측을 최근 실제에 맞춰 옮긴 양 kW (대신 쓴 예측이면 0)
        List<Slot> slots
) {

    public record Slot(
            LocalDateTime slotStart, // 칸 시작 시각
            double loadKw,           // 평균 예측 kW
            double peakLoadKw        // 높게 본 예측 kW (방어선 판단용)
    ) {
    }
}
