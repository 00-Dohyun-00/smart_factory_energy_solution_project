package com.smartfactory.energy.report.dto;

import java.time.LocalDateTime;

/**
 * 성적표 맨 위 핵심 숫자.
 */
public record ReportSummary(
        int defenseTotal,               // 피크 방어 추천 수
        int defenseSuccess,             // 그중 목표 피크 이하로 끝난 수
        Double defenseSuccessPercent,   // 방어 성공률 %
        Double monthPeakKw,             // 이번 달 최대 피크
        LocalDateTime monthPeakAt,      // 최대 피크가 나온 15분 구간 시작 시각
        Double avgReductionKw,          // 날마다 (배터리 없었다면 피크 − 실제 피크)의 평균
        Double maxReductionKw           // 위 값의 최대
) {
}
