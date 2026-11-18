package com.smartfactory.energy.sitepower;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRepository;
import com.smartfactory.energy.equipment.EquipmentRole;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.ess.EssStatusMinuteRepository;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.power.PowerMinute;
import com.smartfactory.energy.power.PowerMinuteRepository;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.power.SitePowerMinuteRepository;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.EquipmentPower;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.SourceRatio;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.Tariff;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.TariffBand;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.TodayEnergy;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.WeatherNow;
import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse.WindowPoint;
import com.smartfactory.energy.sitepower.dto.SitePowerQuarterResponse;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodRepository;
import com.smartfactory.energy.tariff.TariffPeriodType;
import com.smartfactory.energy.tariff.TariffSeason;
import com.smartfactory.energy.weather.Weather;
import com.smartfactory.energy.weather.WeatherRepository;
import java.math.BigDecimal;
import java.time.Duration;
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
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SitePowerService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SitePowerMinuteRepository sitePowerMinuteRepository;
    private final EssStatusMinuteRepository essStatusMinuteRepository;
    private final DemandInterval15mRepository demandInterval15mRepository;
    private final PowerMinuteRepository powerMinuteRepository;
    private final EquipmentRepository equipmentRepository;
    private final WeatherRepository weatherRepository;
    private final TariffPeriodRepository tariffPeriodRepository;
    private final SettingRepository settingRepository;

    // ---------- 지금 상태 1건 (GET /api/site-powers/latest) ----------

    public SitePowerLatestResponse getLatest() {
        // 1. 최신 1분 기록. 하나도 없으면 404
        SitePowerMinute latest = sitePowerMinuteRepository.findFirstByOrderByTsDesc()
                .orElseThrow(() -> new NotFoundException("아직 전력 기록이 없습니다."));
        EssStatusMinute ess = essStatusMinuteRepository.findFirstByOrderByTsDesc().orElse(null);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDate today = now.toLocalDate();

        // 2. 지금 값. ess_kw는 방전 +, 충전 −
        double essKw = toDouble(latest.getEssKw());
        double loadKw = toDouble(latest.getLoadKw());
        double solarKw = toDouble(latest.getSolarKw());
        double gridKw = toDouble(latest.getGridKw());
        double chargeKw = essKw < 0 ? -essKw : 0;
        double dischargeKw = essKw > 0 ? essKw : 0;

        // 3. 15분 피크
        LocalDateTime windowStart = quarterStart(now);
        LocalDateTime windowEnd = windowStart.plusMinutes(15);
        List<SitePowerMinute> windowRows = sitePowerMinuteRepository.findInPeriod(windowStart, windowEnd);

        // 구간 시작부터 1분씩 쌓인 값 (그 분까지 한전 구매 합 ÷ 15)
        List<WindowPoint> windowPoints = new ArrayList<>();
        double gridSum = 0;
        for (SitePowerMinute row : windowRows) {
            gridSum += toDouble(row.getGridKw());
            int minute = (int) ChronoUnit.MINUTES.between(windowStart, row.getTs()) + 1;
            windowPoints.add(new WindowPoint(minute, round1(gridSum / 15)));
        }

        // 예상 피크 = (지금까지 쌓인 값 + 지금 한전 구매 × 아직 안 쌓인 분) ÷ 15
        int remainingMinutes = Math.max(0, 15 - windowRows.size());
        double predictedKw = (gridSum + gridKw * remainingMinutes) / 15;

        Double targetPeakKw = settingNumber("peak.target_kw");
        Double warnRatio = settingNumber("peak.warn_ratio");
        Double warnKw = targetPeakKw != null && warnRatio != null ? round1(targetPeakKw * warnRatio / 100) : null;

        String peakStatus = "NORMAL";
        if (targetPeakKw != null && predictedKw > targetPeakKw) {
            peakStatus = "CRITICAL";
        } else if (warnKw != null && predictedKw >= warnKw) {
            peakStatus = "WARNING";
        }

        // 추가 방전: 남은 시간 동안 평균을 (예상 − 목표)만큼 낮추려면 지금부터 더 방전해야 하는 kW
        double defenseKw = 0;
        if (targetPeakKw != null && predictedKw > targetPeakKw && remainingMinutes > 0) {
            defenseKw = Math.ceil((predictedKw - targetPeakKw) * 15 / remainingMinutes);
        }

        // 4. 오늘·이번 달 최대 피크 (15분이 다 찬 구간만)
        List<DemandInterval15m> monthIntervals = completeIntervals(
                today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay());
        Double todayPeakKw = maxPeak(monthIntervals.stream()
                .filter(d -> d.getIntervalStart().toLocalDate().equals(today))
                .toList());
        Double monthPeakKw = maxPeak(monthIntervals);

        return new SitePowerLatestResponse(
                now,
                round1(loadKw),
                round1(solarKw),
                round1(chargeKw),
                round1(dischargeKw),
                round1(gridKw),
                ess != null ? round1(toDouble(ess.getSoc())) : null,
                ess != null && ess.getBatteryTemp() != null ? round1(toDouble(ess.getBatteryTemp())) : null,
                ess != null ? pcsStatus(ess) : "STANDBY",
                settingNumber("ess.soh_percent"),
                weatherNow(now),
                1,
                windowStart,
                windowEnd,
                Duration.between(now, windowEnd).getSeconds(),
                round1(predictedKw),
                targetPeakKw,
                warnKw,
                peakStatus,
                defenseKw,
                windowPoints,
                todayPeakKw,
                monthPeakKw,
                tariffAt(now),
                new SourceRatio(percent(gridKw, loadKw), percent(solarKw, loadKw), percent(dischargeKw, loadKw)),
                todayEnergy(today),
                equipments(latest.getTs())
        );
    }

    // ---------- 오늘 15분 간격 목록 (GET /api/site-powers/today) ----------

    public List<SitePowerQuarterResponse> getToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // 15분 구간 시작 시각 → 그 구간의 1분 기록들
        Map<LocalDateTime, List<SitePowerMinute>> byQuarter = new TreeMap<>();
        for (SitePowerMinute row : sitePowerMinuteRepository.findInPeriod(start, end)) {
            byQuarter.computeIfAbsent(quarterStart(row.getTs()), key -> new ArrayList<>()).add(row);
        }

        // 15분 구간 시작 시각 → 그 구간 마지막 1분의 잔량 (시간 순이라 뒤에 오는 값으로 덮어씀)
        Map<LocalDateTime, Double> socByQuarter = new HashMap<>();
        for (EssStatusMinute ess : essStatusMinuteRepository.findInPeriod(start, end)) {
            socByQuarter.put(quarterStart(ess.getTs()), round1(toDouble(ess.getSoc())));
        }

        List<SitePowerQuarterResponse> result = new ArrayList<>();
        for (LocalDateTime quarter : byQuarter.keySet()) {
            List<SitePowerMinute> rows = byQuarter.get(quarter);
            result.add(new SitePowerQuarterResponse(
                    quarter,
                    round1(rows.stream().mapToDouble(r -> toDouble(r.getLoadKw())).average().orElse(0)),
                    round1(rows.stream().mapToDouble(r -> toDouble(r.getSolarKw())).average().orElse(0)),
                    round1(rows.stream().mapToDouble(r -> toDouble(r.getGridKw())).average().orElse(0)),
                    socByQuarter.get(quarter)
            ));
        }
        return result;
    }

    // ---------- 작은 도우미 메서드 ----------

    // 오늘 0시부터 지금까지 누적 kWh (1분 kW ÷ 60을 더함)
    private TodayEnergy todayEnergy(LocalDate today) {
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<SitePowerMinute> rows = sitePowerMinuteRepository.findInPeriod(start, end);
        List<EssStatusMinute> essRows = essStatusMinuteRepository.findInPeriod(start, end);
        return new TodayEnergy(
                round1(rows.stream().mapToDouble(r -> toDouble(r.getGridKw())).sum() / 60),
                round1(rows.stream().mapToDouble(r -> toDouble(r.getSolarKw())).sum() / 60),
                round1(essRows.stream().mapToDouble(e -> toDouble(e.getDischargeKwh())).sum()),
                round1(essRows.stream().mapToDouble(e -> toDouble(e.getChargeKwh())).sum())
        );
    }

    // 설비별 최신 1분 전력 (생산 설비 중 사용 중인 것만, 설비 ID 순)
    private List<EquipmentPower> equipments(LocalDateTime ts) {
        Map<String, PowerMinute> powerById = new HashMap<>();
        for (PowerMinute p : powerMinuteRepository.findByTs(ts)) {
            powerById.put(p.getEquipmentId(), p);
        }
        return equipmentRepository.findByEquipmentRole(EquipmentRole.LOAD).stream()
                .filter(Equipment::getIsActive)
                .sorted(Comparator.comparing(Equipment::getEquipmentId))
                .map(e -> {
                    PowerMinute p = powerById.get(e.getEquipmentId());
                    return new EquipmentPower(
                            e.getEquipmentId(),
                            e.getName(),
                            p != null ? round1(toDouble(p.getAvgKw())) : null,
                            Boolean.TRUE.equals(e.getIsVirtual()) ? "SIMULATED" : "MEASURED",
                            p == null ? null : (p.getIsRunning() != null && p.getIsRunning() == 1 ? "RUNNING" : "STOPPED")
                    );
                })
                .toList();
    }

    // 지금 요금 시간대와 오늘 시간대표
    private Tariff tariffAt(LocalDateTime now) {
        List<TariffPeriod> periods = tariffPeriodRepository.findBySeason(seasonOf(now.toLocalDate())).stream()
                .sorted(Comparator.comparing(TariffPeriod::getStartTime))
                .toList();
        if (periods.isEmpty()) {
            return null;
        }

        int index = -1;
        for (int i = 0; i < periods.size(); i++) {
            if (isInPeriod(periods.get(i), now.toLocalTime())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null; // 요금표에 빈 시각이 있으면 엉뚱한 시간대를 보여주지 않고 비워 둠
        }
        TariffPeriod current = periods.get(index);

        // 다음에 시간대 이름이 바뀌는 칸 (23~24시 경부하 다음의 0시 경부하는 이어진 같은 시간대로 봄)
        int nextIndex = (index + 1) % periods.size();
        while (periods.get(nextIndex).getPeriodType() == current.getPeriodType() && nextIndex != index) {
            nextIndex = (nextIndex + 1) % periods.size();
        }
        TariffPeriod next = periods.get(nextIndex);

        List<TariffBand> bands = periods.stream()
                .map(p -> new TariffBand(p.getStartTime().format(HH_MM), endText(p.getEndTime()),
                        periodCode(p.getPeriodType()), periodLabel(p.getPeriodType())))
                .toList();
        return new Tariff(periodCode(current.getPeriodType()), periodLabel(current.getPeriodType()),
                next.getStartTime().format(HH_MM), periodLabel(next.getPeriodType()), bands);
    }

    // 오늘 가장 최근 날씨 (지금 이전 기록 중 마지막). 없으면 null
    private WeatherNow weatherNow(LocalDateTime now) {
        List<Weather> rows = weatherRepository.findInPeriod(now.toLocalDate().atStartOfDay(), now.plusMinutes(1));
        if (rows.isEmpty()) {
            return null;
        }
        Weather w = rows.get(rows.size() - 1);
        return new WeatherNow(skyText(w), w.getTemperature() != null ? round1(toDouble(w.getTemperature())) : null);
    }

    // 하늘 상태 글자 (분석 화면과 같은 말)
    private String skyText(Weather w) {
        if (w.getSky() == null) {
            return null;
        }
        return switch (w.getSky()) {
            case CLEAR -> "맑음";
            case CLOUDY -> "구름많음";
            case OVERCAST -> "흐림";
        };
    }

    // ESS 상태를 화면 코드로 바꿈
    private String pcsStatus(EssStatusMinute ess) {
        return switch (ess.getStatus()) {
            case CHARGE -> "CHARGING";
            case DISCHARGE -> "DISCHARGING";
            case IDLE -> "STANDBY";
        };
    }

    // 15분이 다 찬 구간만 (진행 중인 구간은 아직 결과가 아님)
    private List<DemandInterval15m> completeIntervals(LocalDateTime start, LocalDateTime end) {
        return demandInterval15mRepository.findInPeriod(start, end).stream()
                .filter(d -> d.getMinuteCount() != null && d.getMinuteCount() >= 15)
                .filter(d -> d.getActualAvgKw() != null)
                .toList();
    }

    // 구간들 중 최대 피크. 없으면 null
    private Double maxPeak(List<DemandInterval15m> intervals) {
        if (intervals.isEmpty()) {
            return null;
        }
        double max = intervals.stream().mapToDouble(d -> d.getActualAvgKw().doubleValue()).max().getAsDouble();
        return round1(max);
    }

    // 15분 단위로 내림 (예: 14:07 → 14:00)
    private LocalDateTime quarterStart(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return hour.plusMinutes((time.getMinute() / 15) * 15L);
    }

    // setting 테이블의 값을 숫자로 읽음. 없으면 null
    private Double settingNumber(String key) {
        return settingRepository.findById(key)
                .map(setting -> Double.valueOf(setting.getValue()))
                .orElse(null);
    }

    // 그 시각이 요금 시간대 안에 있는지. 끝 시각 00:00은 하루 끝(24시)으로 봄
    private boolean isInPeriod(TariffPeriod period, LocalTime time) {
        boolean endsAtMidnight = period.getEndTime().equals(LocalTime.MIDNIGHT);
        return !time.isBefore(period.getStartTime()) && (endsAtMidnight || time.isBefore(period.getEndTime()));
    }

    // 끝 시각 글자. 하루 끝(00:00 또는 23:59 이후)은 "24:00"
    private String endText(LocalTime end) {
        if (end.equals(LocalTime.MIDNIGHT) || !end.isBefore(LocalTime.of(23, 59))) {
            return "24:00";
        }
        return end.format(HH_MM);
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

    // part가 whole의 몇 %인지 (whole이 0이면 0)
    private Double percent(double part, double whole) {
        return whole == 0 ? 0.0 : round1(part * 100 / whole);
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    // 소수 1자리로 반올림 (예: 12.345 → 12.3)
    private Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
