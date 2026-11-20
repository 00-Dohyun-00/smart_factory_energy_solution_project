package com.smartfactory.energy.analysis;

import com.smartfactory.energy.analysis.dto.PeakDaily;
import com.smartfactory.energy.analysis.dto.PeakHeatmapCell;
import com.smartfactory.energy.analysis.dto.PeakResponse;
import com.smartfactory.energy.analysis.dto.PeakSummary;
import com.smartfactory.energy.analysis.dto.PeakWindow;
import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffSeason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeakAnalysisService {

    // 한 번에 조회할 수 있는 최대 기간 (일)
    private static final int MAX_DAYS = 62;

    // 피크가 높았던 구간을 몇 개 보여 줄지
    private static final int TOP_WINDOW_COUNT = 8;

    private final DemandInterval15mRepository demandInterval15mRepository;
    private final RecommendationRepository recommendationRepository;
    private final SettingRepository settingRepository;
    private final TariffPeriodRepository tariffPeriodRepository;

    // 기간 피크 분석
    public PeakResponse getPeak(LocalDate from, LocalDate to) {
        // 1. 요청 값 검사
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        // 2. 기간 안의 15분 구간 꺼내기. 15분이 다 찬 구간만 씀 (진행 중인 구간은 아직 결과가 아님)
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<DemandInterval15m> intervals = demandInterval15mRepository.findInPeriod(start, end).stream()
                .filter(d -> d.getMinuteCount() != null && d.getMinuteCount() >= 15)
                .filter(d -> d.getActualAvgKw() != null)
                .toList();

        Double targetPeakKw = settingNumber("peak.target_kw");

        // 기록이 하나도 없으면 hasData=false 만 돌려줌
        if (intervals.isEmpty()) {
            return new PeakResponse(from, to, false, targetPeakKw, null, null, null, null, null);
        }

        // 3. 화면 구역별로 만들기
        List<PeakDaily> dailyPeaks = makeDailyPeaks(intervals);
        List<PeakHeatmapCell> heatmap = makeHeatmap(intervals);
        List<PeakWindow> topWindows = makeTopWindows(intervals, start, end);
        List<PeakWindow> exceededWindows = makeExceededWindows(intervals, targetPeakKw);
        PeakSummary summary = makeSummary(intervals, dailyPeaks, heatmap, targetPeakKw, to);

        return new PeakResponse(from, to, true, targetPeakKw, summary, dailyPeaks, heatmap, topWindows,
                exceededWindows);
    }

    // ---------- 일별 최대 피크 (dailyPeaks) ----------

    // 날짜마다 실제 최대 피크와, 배터리가 없었다면 예상 최대 피크
    private List<PeakDaily> makeDailyPeaks(List<DemandInterval15m> intervals) {
        Map<LocalDate, List<DemandInterval15m>> byDate = intervals.stream()
                .collect(Collectors.groupingBy(d -> d.getIntervalStart().toLocalDate(), TreeMap::new, Collectors.toList()));

        List<PeakDaily> result = new ArrayList<>();
        for (LocalDate date : byDate.keySet()) {
            List<DemandInterval15m> rows = byDate.get(date);
            double peakKw = rows.stream().mapToDouble(d -> d.getActualAvgKw().doubleValue()).max().orElse(0);
            // 배터리 없었다면 값이 없는 구간은 실제 값을 씀 (방전이 없던 구간)
            double expectedKw = rows.stream()
                    .mapToDouble(d -> toDouble(d.getNoEssAvgKw() != null ? d.getNoEssAvgKw() : d.getActualAvgKw()))
                    .max().orElse(0);
            result.add(new PeakDaily(date, round1(peakKw), round1(expectedKw)));
        }
        return result;
    }

    // ---------- 히트맵 (heatmap) ----------

    // 날짜 × 시각마다 그 시간 안 15분 구간 중 최대 kW
    private List<PeakHeatmapCell> makeHeatmap(List<DemandInterval15m> intervals) {
        // 키 예: 2026-11-16T14:00 (그 시간의 시작)
        Map<LocalDateTime, Double> maxByHour = new TreeMap<>();
        for (DemandInterval15m d : intervals) {
            LocalDateTime hour = d.getIntervalStart().truncatedTo(ChronoUnit.HOURS);
            double kw = d.getActualAvgKw().doubleValue();
            maxByHour.merge(hour, kw, Math::max); // 이미 있으면 큰 값으로 바꿈
        }

        List<PeakHeatmapCell> result = new ArrayList<>();
        for (LocalDateTime hour : maxByHour.keySet()) {
            result.add(new PeakHeatmapCell(hour.toLocalDate(), hour.getHour(), round1(maxByHour.get(hour))));
        }
        return result;
    }

    // ---------- 피크가 높았던 구간 (topWindows) ----------

    // 날마다 가장 높았던 15분 구간을 고르고, 그중 높은 순으로 8개. 그때의 방전 추천 처리를 같이 붙임
    private List<PeakWindow> makeTopWindows(List<DemandInterval15m> intervals, LocalDateTime start, LocalDateTime end) {
        // 15분 구간 시작 시각 → 그 구간의 대표 방전 추천 (같은 구간에 여러 개면 적용 > 자동 > 넘김 > 대기중)
        Map<LocalDateTime, Recommendation> dischargeByWindow = new HashMap<>();
        for (Recommendation r : recommendationRepository.findInPeriod(start, end)) {
            if (r.getAction() == RecommendationAction.DISCHARGE) {
                dischargeByWindow.merge(r.getIntervalStart(), r, this::pickRepresentative);
            }
        }

        // 날짜마다 가장 높았던 구간 1개
        Map<LocalDate, DemandInterval15m> dayMax = new HashMap<>();
        for (DemandInterval15m d : intervals) {
            dayMax.merge(d.getIntervalStart().toLocalDate(), d,
                    (a, b) -> a.getActualAvgKw().compareTo(b.getActualAvgKw()) >= 0 ? a : b);
        }

        return dayMax.values().stream()
                .sorted(Comparator.comparing(DemandInterval15m::getActualAvgKw).reversed())
                .limit(TOP_WINDOW_COUNT)
                .map(d -> {
                    Recommendation r = dischargeByWindow.get(d.getIntervalStart());
                    return new PeakWindow(
                            d.getIntervalStart(),
                            d.getIntervalStart().plusMinutes(15),
                            round1(d.getActualAvgKw().doubleValue()),
                            r != null ? statusCode(r) : null,
                            r != null && r.getDescription() != null ? r.getDescription() : (r != null ? "방전 추천" : "방전 추천 없음")
                    );
                })
                .toList();
    }

    // ---------- 목표를 넘은 구간 (exceededWindows) ----------

    // 목표 피크보다 높았던 15분 구간 전부 (시간 순). 추이 그래프는 기간이 길면 1시간 평균이라 짧은 피크가 묻히므로,
    // 15분 기준 초과 구간을 따로 줘서 그래프 위에 점으로 찍게 함. 추천 처리는 붙이지 않음 (전력 이력 화면에서 봄)
    private List<PeakWindow> makeExceededWindows(List<DemandInterval15m> intervals, Double targetPeakKw) {
        if (targetPeakKw == null) {
            return List.of();
        }
        return intervals.stream()
                .filter(d -> d.getActualAvgKw().doubleValue() > targetPeakKw)
                .sorted(Comparator.comparing(DemandInterval15m::getIntervalStart))
                .map(d -> new PeakWindow(
                        d.getIntervalStart(),
                        d.getIntervalStart().plusMinutes(15),
                        round1(d.getActualAvgKw().doubleValue()),
                        null,
                        null))
                .toList();
    }

    // ---------- 요약 숫자 (summary) ----------

    private PeakSummary makeSummary(List<DemandInterval15m> intervals, List<PeakDaily> dailyPeaks,
                                    List<PeakHeatmapCell> heatmap, Double targetPeakKw, LocalDate to) {
        // ① 최대 피크와 그 시각
        DemandInterval15m max = intervals.stream()
                .max(Comparator.comparing(DemandInterval15m::getActualAvgKw)).get();

        // ② 목표를 넘은 구간 수 / 전체 구간 수
        int exceededCount = targetPeakKw == null ? 0 : (int) intervals.stream()
                .filter(d -> d.getActualAvgKw().doubleValue() > targetPeakKw)
                .count();

        // ③ 피크 저감: 날마다 (배터리 없었다면 − 실제) → 평균과 최대
        List<Double> reductions = dailyPeaks.stream().map(day -> day.expectedKw() - day.peakKw()).toList();
        double avgReduction = reductions.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxReduction = reductions.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // ④ 피크가 잦은 시간대: 요금 시간대 묶음마다 히트맵 평균 → 가장 높은 묶음
        TariffPeriod busiest = null;
        double busiestAvg = 0;
        for (TariffPeriod period : tariffPeriodRepository.findBySeason(seasonOf(to))) {
            OptionalDouble avg = heatmap.stream()
                    .filter(cell -> isInPeriod(period, LocalTime.of(cell.hour(), 0)))
                    .mapToDouble(PeakHeatmapCell::peakKw)
                    .average();
            if (avg.isEmpty()) {
                continue; // 이 시간대에 기록이 없음
            }
            if (busiest == null || avg.getAsDouble() > busiestAvg) {
                busiest = period;
                busiestAvg = avg.getAsDouble();
            }
        }

        return new PeakSummary(
                round1(max.getActualAvgKw().doubleValue()),
                max.getIntervalStart(),
                exceededCount,
                intervals.size(),
                round1(avgReduction),
                round1(maxReduction),
                busiest != null ? timeText(busiest.getStartTime()) : null,
                busiest != null ? endText(busiest.getEndTime()) : null,
                busiest != null ? periodLabel(busiest) : null
        );
    }

    // ---------- 작은 도우미 메서드 ----------

    // setting 테이블의 값을 숫자로 읽음. 없으면 null
    private Double settingNumber(String key) {
        return settingRepository.findById(key)
                .map(setting -> Double.valueOf(setting.getValue()))
                .orElse(null);
    }

    // 같은 구간의 추천 2개 중 대표 1개 (적용 > 자동 > 넘김·만료 > 대기중, 같으면 나중 추천)
    // 추이 분석(AnalysisService)과 같은 순서라 화면마다 그 구간의 처리 상태가 같게 나옴
    private Recommendation pickRepresentative(Recommendation a, Recommendation b) {
        int rankA = statusRank(a.getStatus());
        int rankB = statusRank(b.getStatus());
        if (rankA != rankB) {
            return rankA < rankB ? a : b;
        }
        return a.getRecommendedAt().isAfter(b.getRecommendedAt()) ? a : b;
    }

    // 대표 추천을 고를 때의 순서 (작을수록 먼저)
    private int statusRank(RecommendationStatus status) {
        return switch (status) {
            case APPLIED -> 0;
            case AUTO -> 1;
            case SKIPPED, EXPIRED -> 2;
            case PENDING -> 3;
        };
    }

    // 추천 처리 상태를 화면 코드로 바꿈 (DB의 SKIPPED·EXPIRED → 화면의 IGNORED, PENDING → RECOMMENDED)
    private String statusCode(Recommendation r) {
        return switch (r.getStatus()) {
            case APPLIED -> "APPLIED";
            case AUTO -> "AUTO";
            case SKIPPED, EXPIRED -> "IGNORED";
            case PENDING -> "RECOMMENDED";
        };
    }

    // 요금 시간대 이름 (경부하 / 중간부하 / 최대부하)
    private String periodLabel(TariffPeriod period) {
        return switch (period.getPeriodType()) {
            case OFF_PEAK -> "경부하";
            case MID_PEAK -> "중간부하";
            case ON_PEAK -> "최대부하";
        };
    }

    // 그 시각이 요금 시간대 안에 있는지. 끝 시각 00:00은 하루 끝(24시)으로 봄
    private boolean isInPeriod(TariffPeriod period, LocalTime time) {
        boolean endsAtMidnight = period.getEndTime().equals(LocalTime.MIDNIGHT);
        return !time.isBefore(period.getStartTime()) && (endsAtMidnight || time.isBefore(period.getEndTime()));
    }

    // 날짜의 계절 (한전 요금 기준: 6~8월 여름, 3~5월·9~10월 봄가을, 나머지 겨울)
    private TariffSeason seasonOf(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 6 && month <= 8) {
            return TariffSeason.SUMMER;
        }
        if (month >= 3 && month <= 10) {
            return TariffSeason.SPRING_FALL;
        }
        return TariffSeason.WINTER;
    }

    // 시각 글자 (예: 17:00)
    private String timeText(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // 끝 시각 글자. 하루 끝(00:00 또는 23:59 이후)은 "24:00" (00:00으로 보이면 "22:00 ~ 00:00"처럼 거꾸로 읽힘)
    private String endText(LocalTime end) {
        if (end.equals(LocalTime.MIDNIGHT) || !end.isBefore(LocalTime.of(23, 59))) {
            return "24:00";
        }
        return timeText(end);
    }

    private double toDouble(BigDecimal value) {
        return value.doubleValue();
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
