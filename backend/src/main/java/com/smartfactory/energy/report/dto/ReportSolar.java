package com.smartfactory.energy.report.dto;

/**
 * 태양광 발전과 탄소 감축 추정.
 */
public record ReportSolar(
        Double generationKwh,     // 태양광 발전량
        Double selfUseKwh,        // 공장에서 직접 쓴 양
        Double selfUsePercent,    // 직접 쓴 비율 %
        Double gridReductionKwh,  // 한전 구매를 줄인 양 (= selfUseKwh)
        Double emissionFactor,    // 탄소 배출계수 kgCO2/kWh (setting: carbon.emission_factor)
        Double carbonTon          // 탄소 감축량 t
) {
}
