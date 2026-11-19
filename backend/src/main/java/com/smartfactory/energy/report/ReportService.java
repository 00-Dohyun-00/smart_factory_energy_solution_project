package com.smartfactory.energy.report;

import com.smartfactory.energy.analysis.PeakAnalysisService;
import com.smartfactory.energy.analysis.dto.PeakResponse;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.report.dto.ReportBill;
import com.smartfactory.energy.report.dto.ReportDailyDischarge;
import com.smartfactory.energy.report.dto.ReportEss;
import com.smartfactory.energy.report.dto.ReportExceeded;
import com.smartfactory.energy.report.dto.ReportResponse;
import com.smartfactory.energy.report.dto.ReportSolar;
import com.smartfactory.energy.report.dto.ReportSummary;
import com.smartfactory.energy.report.dto.ReportTariffUsage;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffPeriodType;
import com.smartfactory.energy.tariff.TariffSeason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final DemandInterval15mRepository demandInterval15mRepository;
    private final EssStatusMinuteRepository essStatusMinuteRepository;
    private final RecommendationRepository recommendationRepository;
    private final TariffPeriodRepository tariffPeriodRepository;
    private final SettingRepository settingRepository;
    private final PeakAnalysisService peakAnalysisService;

    // 월간 성적표
    public ReportResponse getReport(String month) {
        // 1. 요청 값 검사 (예: 2026-11)
        YearMonth yearMonth = parseMonth(month);
        Double targetPeakKw = settingNumber("peak.target_kw");

        // 2. 집계 기간: 그 달 1일 ~ 말일. 이번 달은 오늘까지, 아직 안 온 달은 기록 없음
        LocalDate today = LocalDate.now();
        LocalDate periodStart = yearMonth.atDay(1);
        if (periodStart.isAfter(today)) {
            return ReportResponse.empty(month, targetPeakKw);
        }
        LocalDate periodEnd = yearMonth.atEndOfMonth().isAfter(today) ? today : yearMonth.atEndOfMonth();
        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime end = periodEnd.plusDays(1).atStartOfDay();

        // 3. 그 달 기록 꺼내기. 1분 전력 기록이 없으면 hasData=false
        List<SitePowerMinute> powers = sitePowerMinuteRepository.findInPeriod(start, end);
        if (powers.isEmpty()) {
            return ReportResponse.empty(month, targetPeakKw);
        }
        List<DemandInterval15m> intervals = completeIntervals(start, end);
        List<EssStatusMinute> essList = essStatusMinuteRepository.findInPeriod(start, end);
        List<Recommendation> recommendations = recommendationRepository.findInPeriod(start, end);

        // 15분 구간 시작 시각 → 실제 피크
        Map<LocalDateTime, Double> resultPeaks = new HashMap<>();
        for (DemandInterval15m d : intervals) {
            resultPeaks.put(d.getIntervalStart(), d.getActualAvgKw().doubleValue());
        }

        // 4. 일별 최대 피크·히트맵·최대 피크·저감량은 피크 분석과 같은 계산을 씀 (두 화면 숫자가 같게)
        PeakResponse peak = peakAnalysisService.getPeak(periodStart, periodEnd);

        // 5. 화면 구역별로 만들기
        ReportSummary summary = makeSummary(recommendations, resultPeaks, targetPeakKw, peak);
        Double monthPeakKw = peak.hasData() ? peak.summary().maxPeakKw() : null;
        ReportBill bill = makeBill(yearMonth, powers, monthPeakKw, targetPeakKw);
        List<ReportExceeded> exceeded = makeExceeded(intervals, recommendations, targetPeakKw);
        ReportEss ess = makeEss(essList, recommendations, resultPeaks, targetPeakKw);
        ReportSolar solar = makeSolar(powers);

        // 15분이 다 찬 구간이 아직 없으면 피크 분석이 null을 주므로 빈 목록으로 바꿈 (화면은 "기록 없음" 표시)
        return new ReportResponse(month, true, periodStart, periodEnd, targetPeakKw, summary,
                peak.hasData() ? peak.dailyPeaks() : List.of(),
                peak.hasData() ? peak.heatmap() : List.of(),
                bill, exceeded, ess, solar);
    }

    // ---------- 핵심 숫자 (summary) ----------

    private ReportSummary makeSummary(List<Recommendation> recommendations, Map<LocalDateTime, Double> resultPeaks,
                                      Double targetPeakKw, PeakResponse peak) {
        // 피크 방어 추천 수와, 그중 실제 피크가 목표 이하로 끝난 수
        // 판단 엔진은 1분마다 추천을 낼 수 있어서 같은 15분 구간에 추천이 여러 줄일 수 있음
        // → 추천 줄 수가 아니라 "구간 1개 = 1회"로 셈. 아직 끝나지 않은 구간은 결과가 없어서 뺌
        Map<LocalDateTime, Recommendation> defenseByWindow = latestByWindow(recommendations.stream()
                .filter(r -> r.getReasonType() == RecommendationReasonType.PEAK_DEFENSE)
                .toList());
        int defenseTotal = 0;
        int defenseSuccess = 0;
        for (Map.Entry<LocalDateTime, Recommendation> entry : defenseByWindow.entrySet()) {
            Double result = resultPeaks.get(entry.getKey());
            if (result == null) {
                continue;
            }
            defenseTotal++;
            Double target = targetOf(entry.getValue(), targetPeakKw);
            if (target != null && result <= target) {
                defenseSuccess++;
            }
        }
        Double successPercent = defenseTotal == 0 ? null : round1(defenseSuccess * 100.0 / defenseTotal);

        if (!peak.hasData()) {
            return new ReportSummary(defenseTotal, defenseSuccess, successPercent, null, null, null, null);
        }
        return new ReportSummary(defenseTotal, defenseSuccess, successPercent,
                peak.summary().maxPeakKw(), peak.summary().maxPeakAt(),
                peak.summary().avgReductionKw(), peak.summary().maxReductionKw());
    }

    // ---------- 전기요금 (bill) ----------

    private ReportBill makeBill(YearMonth yearMonth, List<SitePowerMinute> powers, Double monthPeakKw,
                                Double targetPeakKw) {
        Double basePrice = settingNumber("tariff.base_charge");
        List<TariffPeriod> periods = tariffPeriodRepository.findBySeason(seasonOf(yearMonth.atDay(1)));

        // 요금 시간대별 kWh와 요금
        Map<TariffPeriodType, Double> kwhByType = new EnumMap<>(TariffPeriodType.class);
        Map<TariffPeriodType, Double> chargeByType = new EnumMap<>(TariffPeriodType.class);
        for (TariffPeriodType type : TariffPeriodType.values()) {
            kwhByType.put(type, 0.0);
            chargeByType.put(type, 0.0);
        }
        for (SitePowerMinute p : powers) {
            TariffPeriod period = periodAt(periods, p.getTs().toLocalTime());
            if (period == null) {
                continue;
            }
            // 1분 동안 산 전력량 = kW ÷ 60. 태양광이 남아 0보다 작으면 산 것이 없음
            double kwh = Math.max(0, p.getGridKw().doubleValue()) / 60;
            kwhByType.merge(period.getPeriodType(), kwh, Double::sum);
            chargeByType.merge(period.getPeriodType(), kwh * period.getUnitPrice().doubleValue(), Double::sum);
        }

        double totalKwh = kwhByType.values().stream().mapToDouble(Double::doubleValue).sum();
        // 합계는 시간대별로 반올림한 금액을 더해서 만듦 (항목 합과 합계가 1원 어긋나지 않게)
        long energyCharge = 0;
        for (double charge : chargeByType.values()) {
            energyCharge += Math.round(charge);
        }
        long baseCharge = baseCharge(monthPeakKw, basePrice);
        long overTarget = 0;
        if (monthPeakKw != null && targetPeakKw != null && basePrice != null) {
            overTarget = Math.round(Math.max(0, monthPeakKw - targetPeakKw) * basePrice);
        }

        // 월말 예상: 실제 기록된 시간 기준 하루 평균 전력량요금 × 그 달 날수 + 기본요금
        // (달력 날짜로 나누면 기록이 빈 날이나 진행 중인 오늘도 하루로 세서 낮게 나옴)
        double recordedDays = powers.size() / 1440.0; // 1분 기록 1440개 = 하루
        long monthEndEstimate = Math.round(energyCharge / recordedDays * yearMonth.lengthOfMonth()) + baseCharge;

        // 시간대별 비중. 마지막 칸은 100에서 앞 칸들을 뺀 값 (합이 100.1%처럼 어긋나지 않게)
        List<ReportTariffUsage> byTariff = new ArrayList<>();
        TariffPeriodType[] types = TariffPeriodType.values();
        double percentSoFar = 0;
        for (int i = 0; i < types.length; i++) {
            TariffPeriodType type = types[i];
            double kwh = kwhByType.get(type);
            double percent = 0.0;
            if (totalKwh > 0) {
                percent = i < types.length - 1 ? round1(kwh * 100 / totalKwh) : round1(100 - percentSoFar);
            }
            percentSoFar += percent;
            byTariff.add(new ReportTariffUsage(
                    periodCode(type),
                    periodLabel(type),
                    round1(kwh),
                    percent,
                    Math.round(chargeByType.get(type))
            ));
        }

        return new ReportBill(energyCharge + baseCharge, energyCharge, baseCharge, overTarget,
                monthEndEstimate, lastMonthTotal(yearMonth.minusMonths(1), basePrice), byTariff);
    }

    // 전달 요금: 같은 방법으로 전력량요금 + 기본요금. 기록이 없으면 null
    private Long lastMonthTotal(YearMonth lastMonth, Double basePrice) {
        LocalDateTime start = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime end = lastMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<SitePowerMinute> powers = sitePowerMinuteRepository.findInPeriod(start, end);
        if (powers.isEmpty()) {
            return null;
        }
        List<TariffPeriod> periods = tariffPeriodRepository.findBySeason(seasonOf(lastMonth.atDay(1)));
        double energyCharge = 0;
        for (SitePowerMinute p : powers) {
            TariffPeriod period = periodAt(periods, p.getTs().toLocalTime());
            if (period != null) {
                energyCharge += Math.max(0, p.getGridKw().doubleValue()) / 60 * period.getUnitPrice().doubleValue();
            }
        }
        Double lastPeakKw = completeIntervals(start, end).stream()
                .map(d -> d.getActualAvgKw().doubleValue())
                .max(Double::compare)
                .orElse(null);
        return Math.round(energyCharge) + baseCharge(lastPeakKw, basePrice);
    }

    // 기본요금 = 최대 피크 × 기본요금 단가. 값이 없으면 0
    private long baseCharge(Double peakKw, Double basePrice) {
        if (peakKw == null || basePrice == null) {
            return 0;
        }
        return Math.round(peakKw * basePrice);
    }

    // ---------- 목표를 넘은 구간 (exceeded) ----------

    private List<ReportExceeded> makeExceeded(List<DemandInterval15m> intervals, List<Recommendation> recommendations,
                                              Double targetPeakKw) {
        if (targetPeakKw == null) {
            return List.of();
        }
        // 그 구간의 대표 추천 (적용 > 자동 > 넘김 > 대기중). "마지막 추천"으로 고르면 적용한 구간이
        // 나중에 나온 다른 추천 때문에 넘김·대기로 보일 수 있음
        Map<LocalDateTime, Recommendation> recByWindow = new HashMap<>();
        for (Recommendation r : recommendations) {
            recByWindow.merge(r.getIntervalStart(), r, this::pickRepresentative);
        }

        return intervals.stream()
                .filter(d -> d.getActualAvgKw().doubleValue() > targetPeakKw)
                .sorted(Comparator.comparing(DemandInterval15m::getIntervalStart).reversed()) // 최신 순
                .map(d -> {
                    double peakKw = d.getActualAvgKw().doubleValue();
                    Recommendation r = recByWindow.get(d.getIntervalStart());
                    return new ReportExceeded(
                            d.getIntervalStart(),
                            d.getIntervalStart().plusMinutes(15),
                            round1(peakKw),
                            round1(peakKw - targetPeakKw),
                            r != null ? r.getAction().name() : null,
                            r != null ? r.getPowerKw() : null,
                            r != null ? statusCode(r.getStatus()) : null,
                            r != null ? r.getDescription() : "추천 없음"
                    );
                })
                .toList();
    }

    // ---------- ESS 운용 (ess) ----------

    private ReportEss makeEss(List<EssStatusMinute> essList, List<Recommendation> recommendations,
                              Map<LocalDateTime, Double> resultPeaks, Double targetPeakKw) {
        // 날짜별 방전량
        Map<LocalDate, Double> dischargeByDate = new TreeMap<>();
        double dischargeKwh = 0;
        double chargeKwh = 0;
        double socSum = 0;
        Double socMin = null;
        for (EssStatusMinute e : essList) {
            double discharge = e.getDischargeKwh().doubleValue();
            dischargeByDate.merge(e.getTs().toLocalDate(), discharge, Double::sum);
            dischargeKwh += discharge;
            chargeKwh += e.getChargeKwh().doubleValue();
            double soc = e.getSoc().doubleValue();
            socSum += soc;
            socMin = socMin == null ? soc : Math.min(socMin, soc);
        }

        List<ReportDailyDischarge> dailyDischarge = new ArrayList<>();
        for (LocalDate date : dischargeByDate.keySet()) {
            dailyDischarge.add(new ReportDailyDischarge(date, round1(dischargeByDate.get(date))));
        }

        // 적용했는데도 실제 피크가 목표를 넘은 횟수 (같은 구간은 1회로 셈)
        Map<LocalDateTime, Recommendation> appliedByWindow = latestByWindow(recommendations.stream()
                .filter(r -> r.getStatus() == RecommendationStatus.APPLIED)
                .toList());
        int blockedCount = 0;
        for (Map.Entry<LocalDateTime, Recommendation> entry : appliedByWindow.entrySet()) {
            Double result = resultPeaks.get(entry.getKey());
            Double target = targetOf(entry.getValue(), targetPeakKw);
            if (result != null && target != null && result > target) {
                blockedCount++;
            }
        }

        Double capacityKwh = settingNumber("ess.capacity_kwh");
        return new ReportEss(
                dailyDischarge,
                round1(dischargeKwh),
                round1(chargeKwh),
                chargeKwh == 0 ? null : round1(dischargeKwh * 100 / chargeKwh),
                capacityKwh == null || capacityKwh == 0 ? null : round1(dischargeKwh / capacityKwh),
                essList.isEmpty() ? null : round1(socSum / essList.size()),
                socMin == null ? null : round1(socMin),
                blockedCount
        );
    }

    // ---------- 태양광·탄소 (solar) ----------

    private ReportSolar makeSolar(List<SitePowerMinute> powers) {
        double generationKwh = 0;
        double selfUseKwh = 0;
        for (SitePowerMinute p : powers) {
            double solarKw = p.getSolarKw().doubleValue();
            generationKwh += solarKw / 60;
            // 공장에서 바로 쓴 양 = 1분마다 min(태양광, 공장 소비)
            selfUseKwh += Math.min(solarKw, p.getLoadKw().doubleValue()) / 60;
        }
        Double emissionFactor = settingNumber("carbon.emission_factor");
        return new ReportSolar(
                round1(generationKwh),
                round1(selfUseKwh),
                generationKwh == 0 ? null : round1(selfUseKwh * 100 / generationKwh),
                round1(selfUseKwh),
                emissionFactor,
                emissionFactor == null ? null : round1(selfUseKwh * emissionFactor / 1000)
        );
    }

    // ---------- 작은 도우미 메서드 ----------

    // "2026-11" → YearMonth. 형식이 틀리면 400
    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month 형식이 올바르지 않습니다. (예: 2026-11)");
        }
    }

    // 15분 구간 시작 시각 → 그 구간의 마지막 추천
    private Map<LocalDateTime, Recommendation> latestByWindow(List<Recommendation> recommendations) {
        Map<LocalDateTime, Recommendation> result = new HashMap<>();
        for (Recommendation r : recommendations) {
            result.merge(r.getIntervalStart(), r,
                    (a, b) -> a.getRecommendedAt().isAfter(b.getRecommendedAt()) ? a : b);
        }
        return result;
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

    // 추천에 적힌 목표 피크. 없으면 지금 설정값
    private Double targetOf(Recommendation r, Double targetPeakKw) {
        return r.getTargetPeakKw() != null ? r.getTargetPeakKw().doubleValue() : targetPeakKw;
    }

    // 15분이 다 찬 구간만 (진행 중인 구간은 아직 결과가 아님)
    private List<DemandInterval15m> completeIntervals(LocalDateTime start, LocalDateTime end) {
        return demandInterval15mRepository.findInPeriod(start, end).stream()
                .filter(d -> d.getMinuteCount() != null && d.getMinuteCount() >= 15)
                .filter(d -> d.getActualAvgKw() != null)
                .toList();
    }

    // setting 테이블의 값을 숫자로 읽음. 없으면 null
    private Double settingNumber(String key) {
        return settingRepository.findById(key)
                .map(setting -> Double.valueOf(setting.getValue()))
                .orElse(null);
    }

    // 그 시각이 들어가는 요금 시간대. 끝 시각 00:00은 하루 끝(24시)으로 봄
    private TariffPeriod periodAt(List<TariffPeriod> periods, LocalTime time) {
        for (TariffPeriod period : periods) {
            boolean endsAtMidnight = period.getEndTime().equals(LocalTime.MIDNIGHT);
            if (!time.isBefore(period.getStartTime()) && (endsAtMidnight || time.isBefore(period.getEndTime()))) {
                return period;
            }
        }
        return null;
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

    // 요금 시간대를 화면 코드로 바꿈
    private String periodCode(TariffPeriodType type) {
        return switch (type) {
            case OFF_PEAK -> "LIGHT";
            case MID_PEAK -> "MID";
            case ON_PEAK -> "PEAK";
        };
    }

    // 요금 시간대 이름
    private String periodLabel(TariffPeriodType type) {
        return switch (type) {
            case OFF_PEAK -> "경부하";
            case MID_PEAK -> "중간부하";
            case ON_PEAK -> "최대부하";
        };
    }

    // 처리 상태를 화면 코드로 바꿈 (SKIPPED·EXPIRED → IGNORED, PENDING → RECOMMENDED)
    private String statusCode(RecommendationStatus status) {
        return switch (status) {
            case APPLIED -> "APPLIED";
            case AUTO -> "AUTO";
            case SKIPPED, EXPIRED -> "IGNORED";
            case PENDING -> "RECOMMENDED";
        };
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
