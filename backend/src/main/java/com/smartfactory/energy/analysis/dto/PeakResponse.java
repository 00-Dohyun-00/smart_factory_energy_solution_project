package com.smartfactory.energy.analysis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 피크 분석 응답 전체. 기록이 없으면 hasData=false 이고 summary 이하는 null.
 */
public record PeakResponse(
        LocalDate from,
        LocalDate to,
        boolean hasData,
        Double targetPeakKw,             // 목표 피크 (setting: peak.target_kw)
        PeakSummary summary,             // 위쪽 요약 숫자 4칸
        List<PeakDaily> dailyPeaks,      // 일별 최대 피크와 저감량 그래프
        List<PeakHeatmapCell> heatmap,   // 날짜 × 시각 히트맵
        List<PeakWindow> topWindows,     // 피크가 높았던 구간 8개
        List<PeakWindow> exceededWindows // 목표 피크를 넘은 15분 구간 전부 (시간 순). 추이 그래프의 점(●)용
) {
}
