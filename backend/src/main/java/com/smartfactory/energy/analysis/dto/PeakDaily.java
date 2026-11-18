package com.smartfactory.energy.analysis.dto;

import java.time.LocalDate;

/**
 * 하루 최대 피크 1개. expectedKw - peakKw 가 그날 배터리로 낮춘 양.
 */
public record PeakDaily(
        LocalDate date,
        Double peakKw,      // 실제 최대 피크 kW
        Double expectedKw   // 배터리가 없었다면 예상 최대 피크 kW
) {
}
