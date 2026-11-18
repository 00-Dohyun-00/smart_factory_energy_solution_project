package com.smartfactory.energy.analysis.dto;

import java.time.LocalDateTime;

/**
 * 피크 분석 요약 숫자 4칸.
 */
public record PeakSummary(
        Double maxPeakKw,          // 기간 중 최대 피크 kW
        LocalDateTime maxPeakAt,   // 그 15분 구간 시작 시각
        int exceededCount,         // 목표를 넘은 15분 구간 수
        int windowCount,           // 기간 안의 (다 찬) 15분 구간 수
        Double avgReductionKw,     // 하루 평균 피크 저감 kW (배터리 없었다면 피크 − 실제 피크)
        Double maxReductionKw,     // 가장 많이 낮춘 날의 저감 kW
        String busyFrom,           // 피크가 잦은 시간대 시작 (예: 17:00). 요금표 없으면 null
        String busyTo,             // 피크가 잦은 시간대 끝 (예: 20:00)
        String busyPeriodLabel     // 그 시간대 이름 (경부하 / 중간부하 / 최대부하)
) {
}
