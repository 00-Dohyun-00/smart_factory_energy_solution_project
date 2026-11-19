package com.smartfactory.energy.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 판단 엔진이 가장 최근에 짠 24시간 ESS 계획 (지금 15분 칸부터 96칸).
 * 칸마다 예상 부하·태양광, 계획한 충·방전, 그 칸이 끝날 때의 예상 잔량이 들어 있습니다.
 */
public record EssPlanResponse(
        LocalDateTime plannedAt,     // 계획을 짠 시각
        double startSocPercent,      // 계획을 시작할 때 잔량 %
        double lineKw,               // 방어선 kW (보통 목표 피크. 배터리로 다 못 막는 날은 막을 수 있는 가장 낮은 선)
        double marginKw,             // 방어선 아래로 둔 여유 kW
        String loadForecastMethod,   // 부하 예측 방법 (예: "최근 1시간 평균")
        List<Slot> slots
) {

    public record Slot(
            LocalDateTime slotStart, // 칸 시작 시각
            double loadKw,           // 예상 부하 kW
            double solarKw,          // 예상 태양광 kW
            double netKw,            // ESS 없이 한전 구매 예상 kW (음수면 남는 태양광)
            double price,            // 전력량 단가 원/kWh
            String periodType,       // 요금 시간대 OFF_PEAK / MID_PEAK / ON_PEAK
            double essKw,            // 계획한 ESS 출력 kW (+ 방전 / − 충전)
            double gridKw,           // 계획대로 하면 한전 구매 kW (0 미만은 0, 남는 태양광은 버려짐)
            double socPercent        // 이 칸이 끝날 때 예상 잔량 %
    ) {
    }
}
