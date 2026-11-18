package com.smartfactory.energy.analysis;

import com.smartfactory.energy.analysis.dto.TrendHighlight;
import com.smartfactory.energy.analysis.dto.TrendHourly;
import com.smartfactory.energy.analysis.dto.TrendPoint;
import com.smartfactory.energy.analysis.dto.TrendResponse;
import com.smartfactory.energy.analysis.dto.TrendStatusResult;
import com.smartfactory.energy.analysis.dto.TrendSummary;
import com.smartfactory.energy.analysis.dto.TrendWeather;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationRepository;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffSeason;
import com.smartfactory.energy.weather.Sky;
import com.smartfactory.energy.weather.Weather;
import com.smartfactory.energy.weather.WeatherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    // 한 번에 조회할 수 있는 최대 기간 (일)
    private static final int MAX_DAYS = 62;

    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final EssStatusMinuteRepository essStatusMinuteRepository;
    private final WeatherRepository weatherRepository;
    private final SettingRepository settingRepository;
    private final TariffPeriodRepository tariffPeriodRepository;
    private final RecommendationRepository recommendationRepository;

    // 기간 추이 분석
    public TrendResponse getTrend(LocalDate from, LocalDate to, String interval) {
        // 1. 요청 값 검사
        if (!List.of("QUARTER", "HOUR", "DAY").contains(interval)) {
            throw new IllegalArgumentException("interval은 QUARTER, HOUR, DAY 중 하나여야 합니다.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        // 2. 기간 안의 기록 꺼내기 (from 00:00 이상 ~ to 다음 날 00:00 미만)
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<SitePowerMinute> powers = sitePowerMinuteRepository.findInPeriod(start, end);
        List<EssStatusMinute> essList = essStatusMinuteRepository.findInPeriod(start, end);
        List<Weather> weathers = weatherRepository.findInPeriod(start, end);
        List<Recommendation> recommendations = recommendationRepository.findInPeriod(start, end);

        // 3. 기준값 읽기 (setting 테이블)
        Double targetPeakKw = settingNumber("peak.target_kw");

        // 4. 15분 피크 계산 (요약, 처리별 결과, 눈에 띄는 구간이 같이 씀)
        Map<LocalDateTime, Double> quarterPeaks = makeQuarterPeaks(powers);

        // 5. 화면 구역별로 만들어서 응답 하나로 묶기
        return new TrendResponse(
                from,
                to,
                interval,
                targetPeakKw,
                settingNumber("ess.soc_low"),
                settingNumber("ess.soc_high"),
                settingNumber("ess.room_temp_limit"),
                makeSummary(powers, essList, quarterPeaks),
                makePoints(powers, essList, weathers, interval),
                makeWeather(weathers),
                makeHourly(powers, to),
                makeByStatus(recommendations, quarterPeaks, powers),
                makeHighlights(powers, essList, quarterPeaks, targetPeakKw)
        );
    }

    // ---------- 그래프 점 (points) ----------

    // interval 단위로 묶어 평균 낸 점 목록
    private List<TrendPoint> makePoints(List<SitePowerMinute> powers, List<EssStatusMinute> essList,
                                        List<Weather> weathers, String interval) {
        // 구간 시작 시각별로 묶기 (예: HOUR면 14:00~14:59 기록이 모두 14:00 묶음으로)
        // TreeMap은 키(시각) 순서대로 정렬해 줌
        Map<LocalDateTime, List<SitePowerMinute>> powerGroups = powers.stream()
                .collect(Collectors.groupingBy(p -> bucketStart(p.getTs(), interval), TreeMap::new, Collectors.toList()));
        Map<LocalDateTime, List<EssStatusMinute>> essGroups = essList.stream()
                .collect(Collectors.groupingBy(e -> bucketStart(e.getTs(), interval)));

        // 날씨는 보통 1시간에 1줄이라, 15분 간격이어도 1시간 단위로 묶어서 같은 시간의 값을 씀
        String weatherInterval = interval.equals("QUARTER") ? "HOUR" : interval;
        Map<LocalDateTime, List<Weather>> weatherGroups = weathers.stream()
                .collect(Collectors.groupingBy(w -> bucketStart(w.getTs(), weatherInterval)));

        // 묶음마다 평균을 내서 점 1개로 만들기
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDateTime time : powerGroups.keySet()) {
            List<SitePowerMinute> powerRows = powerGroups.get(time);
            List<EssStatusMinute> essRows = essGroups.getOrDefault(time, List.of());
            List<Weather> weatherRows = weatherGroups.getOrDefault(bucketStart(time, weatherInterval), List.of());

            points.add(new TrendPoint(
                    time,
                    average(powerRows.stream().map(SitePowerMinute::getLoadKw).toList()),
                    average(powerRows.stream().map(SitePowerMinute::getGridKw).toList()),
                    average(powerRows.stream().map(SitePowerMinute::getSolarKw).toList()),
                    average(powerRows.stream().map(SitePowerMinute::getEssKw).toList()),
                    average(essRows.stream().map(EssStatusMinute::getSoc).toList()),
                    average(essRows.stream().map(EssStatusMinute::getBatteryTemp).toList()),
                    average(weatherRows.stream().map(Weather::getTemperature).toList())
            ));
        }
        return points;
    }

    // ---------- 요약 숫자 (summary) ----------

    // 요약 숫자 6칸. 공장 전력 기록이 하나도 없으면 null
    private TrendSummary makeSummary(List<SitePowerMinute> powers, List<EssStatusMinute> essList,
                                     Map<LocalDateTime, Double> quarterPeaks) {
        if (powers.isEmpty()) {
            return null;
        }

        // ① 피크: 15분 평균 한전 구매 중 가장 큰 값
        Map.Entry<LocalDateTime, Double> maxPeak = quarterPeaks.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);

        // ② 평균 한전 구매
        Double avgGridKw = average(powers.stream().map(SitePowerMinute::getGridKw).toList());

        // ③ 태양광 발전량: 1분 기록이라 kW × (1/60)시간 = kWh 를 모두 더함
        double solarKwh = sum(powers, SitePowerMinute::getSolarKw) / 60;
        double loadKwh = sum(powers, SitePowerMinute::getLoadKw) / 60;
        Double solarPercent = loadKwh > 0 ? round1(solarKwh / loadKwh * 100) : null;

        // ④ ESS 충·방전량과 효율 (방전량 ÷ 충전량)
        double dischargeKwh = sum(essList, EssStatusMinute::getDischargeKwh);
        double chargeKwh = sum(essList, EssStatusMinute::getChargeKwh);
        Double efficiency = chargeKwh > 0 ? round1(dischargeKwh / chargeKwh * 100) : null;

        // ⑤ 배터리 잔량 최저·최고
        EssStatusMinute minSoc = pickMin(essList, EssStatusMinute::getSoc);
        EssStatusMinute maxSoc = pickMax(essList, EssStatusMinute::getSoc);

        // ⑥ 배터리실 최고 온도
        EssStatusMinute maxRoom = pickMax(essList, EssStatusMinute::getBatteryTemp);

        return new TrendSummary(
                maxPeak != null ? maxPeak.getValue() : null,
                maxPeak != null ? maxPeak.getKey() : null,
                avgGridKw,
                round1(solarKwh),
                solarPercent,
                round1(dischargeKwh),
                round1(chargeKwh),
                efficiency,
                minSoc != null ? round1(minSoc.getSoc().doubleValue()) : null,
                minSoc != null ? minSoc.getTs() : null,
                maxSoc != null ? round1(maxSoc.getSoc().doubleValue()) : null,
                maxRoom != null ? round1(maxRoom.getBatteryTemp().doubleValue()) : null,
                maxRoom != null ? maxRoom.getTs() : null
        );
    }

    // ---------- 날짜별 날씨 (weather) ----------

    // 날짜마다 가장 많이 나온 하늘 상태 1개
    private List<TrendWeather> makeWeather(List<Weather> weathers) {
        Map<LocalDate, List<Weather>> byDate = weathers.stream()
                .filter(w -> w.getSky() != null)
                .collect(Collectors.groupingBy(w -> w.getTs().toLocalDate(), TreeMap::new, Collectors.toList()));

        List<TrendWeather> result = new ArrayList<>();
        for (LocalDate date : byDate.keySet()) {
            // 하늘 상태별 개수 세기 (예: 맑음 15, 흐림 9) → 가장 많은 것
            Map<Sky, Long> counts = byDate.get(date).stream()
                    .collect(Collectors.groupingBy(Weather::getSky, Collectors.counting()));
            Sky mostSky = counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            result.add(new TrendWeather(date, skyText(mostSky)));
        }
        return result;
    }

    // ---------- 시각별 평균 한전 구매 (hourly) ----------

    // 0~23시마다 평균 한전 구매 + 요금 시간대 (요금 시간대는 기간 마지막 날의 계절 기준)
    private List<TrendHourly> makeHourly(List<SitePowerMinute> powers, LocalDate to) {
        Map<Integer, List<SitePowerMinute>> byHour = powers.stream()
                .collect(Collectors.groupingBy(p -> p.getTs().getHour()));
        List<TariffPeriod> periods = tariffPeriodRepository.findBySeason(seasonOf(to));

        List<TrendHourly> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<SitePowerMinute> rows = byHour.getOrDefault(hour, List.of());
            Double avgGridKw = average(rows.stream().map(SitePowerMinute::getGridKw).toList());
            result.add(new TrendHourly(hour, avgGridKw, periodAt(periods, LocalTime.of(hour, 0))));
        }
        return result;
    }

    // ---------- 추천 처리별 결과 (byStatus) ----------

    // 방전 추천을 적용·자동·넘김으로 나눠서 몇 번 막았는지 셈
    private List<TrendStatusResult> makeByStatus(List<Recommendation> recommendations,
                                                 Map<LocalDateTime, Double> quarterPeaks,
                                                 List<SitePowerMinute> powers) {
        // 15분이 다 찬 구간만 결과로 인정 (진행 중인 구간은 아직 결과가 없음)
        Map<LocalDateTime, Long> minuteCounts = powers.stream()
                .collect(Collectors.groupingBy(p -> bucketStart(p.getTs(), "QUARTER"), Collectors.counting()));

        List<TrendStatusResult> result = new ArrayList<>();
        for (RecommendationStatus status : List.of(
                RecommendationStatus.APPLIED, RecommendationStatus.AUTO, RecommendationStatus.SKIPPED)) {
            List<Double> resultPeaks = new ArrayList<>();
            int defendedCount = 0;

            for (Recommendation r : recommendations) {
                if (r.getAction() != RecommendationAction.DISCHARGE || r.getStatus() != status) {
                    continue;
                }
                LocalDateTime window = r.getIntervalStart();
                if (minuteCounts.getOrDefault(window, 0L) < 15) {
                    continue; // 결과가 아직 없는 추천은 빼고 셈
                }
                Double resultPeak = quarterPeaks.get(window);
                resultPeaks.add(resultPeak);
                if (r.getTargetPeakKw() != null && resultPeak <= r.getTargetPeakKw().doubleValue()) {
                    defendedCount++;
                }
            }

            int count = resultPeaks.size();
            // 응답 코드는 화면과 맞춘 값 그대로 (SKIPPED → IGNORED)
            result.add(new TrendStatusResult(
                    status == RecommendationStatus.SKIPPED ? "IGNORED" : status.name(),
                    count,
                    defendedCount,
                    count > 0 ? round1(defendedCount * 100.0 / count) : null,
                    average(resultPeaks)
            ));
        }
        return result;
    }

    // ---------- 눈에 띄는 구간 (highlights) ----------

    // 최대 피크, 잔량 최저, 온도 최고, 한낮 태양광 최저, 한전 구매 최저
    private List<TrendHighlight> makeHighlights(List<SitePowerMinute> powers, List<EssStatusMinute> essList,
                                                Map<LocalDateTime, Double> quarterPeaks, Double targetPeakKw) {
        List<TrendHighlight> result = new ArrayList<>();

        quarterPeaks.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(peak -> {
            boolean over = targetPeakKw != null && peak.getValue() > targetPeakKw;
            result.add(new TrendHighlight(peak.getKey(), "15분 피크 최대", peak.getValue(), "kW", over ? "CRITICAL" : "INFO"));
        });

        EssStatusMinute minSoc = pickMin(essList, EssStatusMinute::getSoc);
        if (minSoc != null) {
            result.add(new TrendHighlight(minSoc.getTs(), "배터리 잔량 최저", round1(minSoc.getSoc().doubleValue()), "%", "INFO"));
        }

        EssStatusMinute maxRoom = pickMax(essList, EssStatusMinute::getBatteryTemp);
        if (maxRoom != null) {
            result.add(new TrendHighlight(maxRoom.getTs(), "배터리실 온도 최고", round1(maxRoom.getBatteryTemp().doubleValue()), "℃", "INFO"));
        }

        // 한낮(12시대)에 태양광이 가장 적게 나온 때 (흐린 날 찾기용)
        List<SitePowerMinute> noonRows = powers.stream().filter(p -> p.getTs().getHour() == 12).toList();
        SitePowerMinute minSolar = pickMin(noonRows, SitePowerMinute::getSolarKw);
        if (minSolar != null) {
            result.add(new TrendHighlight(minSolar.getTs(), "한낮 태양광 발전 최저", round1(minSolar.getSolarKw().doubleValue()), "kW", "INFO"));
        }

        SitePowerMinute minGrid = pickMin(powers, SitePowerMinute::getGridKw);
        if (minGrid != null) {
            result.add(new TrendHighlight(minGrid.getTs(), "한전 구매전력 최저", round1(minGrid.getGridKw().doubleValue()), "kW", "INFO"));
        }
        return result;
    }

    // ---------- 작은 도우미 메서드 ----------

    // 15분 구간마다 한전 구매 평균 (= 그 구간의 피크 값). 한전이 기본요금을 매기는 방식
    // 15분이 다 찬 구간만 씀 (피크 분석과 같은 기준. 진행 중인 구간은 아직 결과가 아님)
    private Map<LocalDateTime, Double> makeQuarterPeaks(List<SitePowerMinute> powers) {
        Map<LocalDateTime, List<SitePowerMinute>> groups = powers.stream()
                .collect(Collectors.groupingBy(p -> bucketStart(p.getTs(), "QUARTER")));
        Map<LocalDateTime, Double> result = new TreeMap<>();
        for (LocalDateTime time : groups.keySet()) {
            if (groups.get(time).size() < 15) {
                continue;
            }
            Double avg = average(groups.get(time).stream().map(SitePowerMinute::getGridKw).toList());
            if (avg != null) {
                result.put(time, avg);
            }
        }
        return result;
    }

    // setting 테이블의 값을 숫자로 읽음. 없으면 null
    private Double settingNumber(String key) {
        return settingRepository.findById(key)
                .map(setting -> Double.valueOf(setting.getValue()))
                .orElse(null);
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

    // 그 시각이 속한 요금 시간대 (LIGHT / MID / PEAK). 끝 시각 00:00은 하루 끝(24시)으로 봄
    // DB 값(OFF_PEAK / MID_PEAK / ON_PEAK)을 화면에서 쓰는 코드로 바꿔서 돌려줌
    private String periodAt(List<TariffPeriod> periods, LocalTime time) {
        for (TariffPeriod p : periods) {
            boolean endsAtMidnight = p.getEndTime().equals(LocalTime.MIDNIGHT);
            if (!time.isBefore(p.getStartTime()) && (endsAtMidnight || time.isBefore(p.getEndTime()))) {
                return switch (p.getPeriodType()) {
                    case OFF_PEAK -> "LIGHT";
                    case MID_PEAK -> "MID";
                    case ON_PEAK -> "PEAK";
                };
            }
        }
        return null;
    }

    // 하늘 상태를 화면에 보일 글자로 (CLEAR → 맑음)
    private String skyText(Sky sky) {
        return switch (sky) {
            case CLEAR -> "맑음";
            case CLOUDY -> "구름많음";
            case OVERCAST -> "흐림";
        };
    }

    // 시각이 속한 구간의 시작 시각 (예: 14:37 → QUARTER 14:30, HOUR 14:00, DAY 00:00)
    private LocalDateTime bucketStart(LocalDateTime time, String interval) {
        return switch (interval) {
            case "QUARTER" -> time.truncatedTo(ChronoUnit.HOURS).plusMinutes(time.getMinute() / 15 * 15);
            case "HOUR" -> time.truncatedTo(ChronoUnit.HOURS);
            default -> time.truncatedTo(ChronoUnit.DAYS);
        };
    }

    // 값 목록의 평균 (소수 1자리). 값이 하나도 없으면 null
    // Entity 값은 BigDecimal, 계산 중 값은 Double이라 둘 다 받을 수 있게 Number로 받음
    private Double average(List<? extends Number> values) {
        List<? extends Number> notNullValues = values.stream().filter(Objects::nonNull).toList();
        if (notNullValues.isEmpty()) {
            return null;
        }
        return round1(notNullValues.stream().mapToDouble(Number::doubleValue).average().orElse(0));
    }

    // 목록에서 한 칸의 합계 (null은 빼고 더함)
    private <T> double sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).sum();
    }

    // 목록에서 한 칸의 값이 가장 작은 줄. 없으면 null
    private <T> T pickMin(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().filter(row -> field.apply(row) != null)
                .min(Comparator.comparing(field)).orElse(null);
    }

    // 목록에서 한 칸의 값이 가장 큰 줄. 없으면 null
    private <T> T pickMax(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().filter(row -> field.apply(row) != null)
                .max(Comparator.comparing(field)).orElse(null);
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
