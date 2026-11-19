package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.tariff.TariffPeriodType;
import java.time.LocalDateTime;

/**
 * 계획에 넣는 15분 칸 1개의 값 (ESS 없이).
 * netKw(한전 구매 예상) = 예상 부하 − 예상 태양광. 태양광이 더 많으면 음수 (남는 전기)
 */
public record PlanInput(
        LocalDateTime slotStart,     // 칸 시작 시각
        double loadKw,               // 예상 부하 (평균)
        double peakLoadKw,           // 예상 부하 (높게 본 값, 방어선 판단용)
        double solarKw,              // 예상 태양광 (Open-Meteo)
        double price,                // 그 칸의 전력량 단가 원/kWh
        TariffPeriodType periodType  // 그 칸의 요금 시간대 (없으면 null)
) {

    // 요금 계산에 쓰는 한전 구매 예상
    public double netKw() {
        return loadKw - solarKw;
    }

    // 방어선을 지키는지 볼 때 쓰는 한전 구매 예상 (높게 본 값)
    public double peakNetKw() {
        return peakLoadKw - solarKw;
    }
}
