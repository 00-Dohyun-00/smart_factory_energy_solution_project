package com.smartfactory.energy.report.dto;

import com.smartfactory.energy.analysis.dto.PeakDaily;
import com.smartfactory.energy.analysis.dto.PeakHeatmapCell;
import java.time.LocalDate;
import java.util.List;

/**
 * 월간 성적표 응답 전체. 기록이 없는 달은 month, hasData, targetPeakKw 외에는 null.
 */
public record ReportResponse(
        String month,                   // 요청한 달 (예: 2026-11)
        boolean hasData,
        LocalDate periodStart,          // 집계 시작일 (그 달 1일)
        LocalDate periodEnd,            // 집계 끝날 (이번 달은 오늘)
        Double targetPeakKw,            // 목표 피크 (setting: peak.target_kw)
        ReportSummary summary,          // 핵심 숫자
        List<PeakDaily> dailyPeaks,     // 일별 최대 피크 (피크 분석과 같은 모양)
        List<PeakHeatmapCell> heatmap,  // 날짜 × 시각 히트맵 (피크 분석과 같은 모양)
        ReportBill bill,                // 전기요금 추정
        List<ReportExceeded> exceeded,  // 목표를 넘은 15분 구간 (최신 순)
        ReportEss ess,                  // ESS 운용
        ReportSolar solar               // 태양광·탄소
) {

    // 기록이 없는 달
    public static ReportResponse empty(String month, Double targetPeakKw) {
        return new ReportResponse(month, false, null, null, targetPeakKw,
                null, null, null, null, null, null, null);
    }
}
