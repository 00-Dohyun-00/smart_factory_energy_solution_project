package com.smartfactory.energy.weather;

import com.smartfactory.energy.setting.Setting;
import com.smartfactory.energy.setting.SettingRepository;
import com.smartfactory.energy.weather.dto.OpenMeteoResponse;
import com.smartfactory.energy.weather.dto.SolarForecastSlot;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Open-Meteo에서 받은 가장 최근 15분 예보를 메모리에 들고 있다가, 일사량으로 태양광 발전량을 계산합니다.
 * 발전량은 다시 계산할 수 있는 값이라 DB에 저장하지 않고 필요할 때 계산합니다.
 *
 * 예보는 날씨를 받는 서버(시뮬레이터를 켠 서버, WeatherCollector)에만 들어 있습니다.
 * 다른 서버에서는 비어 있어서 발전량이 없음(Optional.empty / 0)으로 나옵니다.
 *
 * 발전량 kW = 일사량 W/m² × solar.capacity_kw × solar.performance_ratio ÷ 1000 (0 ~ 용량으로 자름)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SolarForecastService {

    // setting에 값이 없을 때 쓰는 기본값
    static final double DEFAULT_CAPACITY_KW = 1000;
    static final double DEFAULT_PERFORMANCE_RATIO = 0.8;

    private static final String CAPACITY_KEY = "solar.capacity_kw";
    private static final String PERFORMANCE_RATIO_KEY = "solar.performance_ratio";
    private static final Duration SLOT = Duration.ofMinutes(15);
    private static final Duration HALF_SLOT = Duration.ofSeconds(450);

    private final SettingRepository settingRepository;

    // 예보 1칸: end = Open-Meteo 시각 (일사량은 end 직전 15분의 평균)
    record Point(LocalDateTime end, double radiationWm2, Double temperature, Double cloudCover) {
    }

    private volatile List<Point> points = List.of();
    private volatile LocalDateTime fetchedAt;

    // 새로 받은 예보로 바꿈. 일사량이 비어 있는 칸은 뺌
    public void update(OpenMeteoResponse response) {
        if (response == null || response.minutely15() == null || response.minutely15().time() == null) {
            return;
        }
        OpenMeteoResponse.Minutely15 m = response.minutely15();
        List<Point> next = new ArrayList<>();
        for (int i = 0; i < m.time().size(); i++) {
            Double radiation = valueAt(m.radiationWm2(), i);
            if (m.time().get(i) == null || radiation == null) {
                continue;
            }
            next.add(new Point(m.time().get(i), radiation, valueAt(m.temperature(), i), valueAt(m.cloudCover(), i)));
        }
        if (next.isEmpty()) {
            return;
        }
        points = List.copyOf(next);
        fetchedAt = response.current() != null && response.current().time() != null
                ? response.current().time()
                : next.get(0).end();
    }

    // 가장 최근 예보를 받은 15분 칸 (예보가 없으면 null). 예보가 얼마나 오래됐는지 볼 때 씀
    public LocalDateTime fetchedAt() {
        return fetchedAt;
    }

    // 그 시각의 일사량 W/m². 칸 평균을 칸 가운데 시각에 두고, 사이는 앞뒤 칸을 직선으로 이음
    // (15분마다 계단처럼 뛰지 않게). 예보 범위 밖이면 empty
    public Optional<Double> radiationAt(LocalDateTime time) {
        List<Point> list = points;
        if (list.isEmpty()) {
            return Optional.empty();
        }
        Point first = list.get(0);
        Point last = list.get(list.size() - 1);
        if (time.isBefore(first.end().minus(SLOT)) || !time.isBefore(last.end())) {
            return Optional.empty();
        }
        if (!time.isAfter(centerOf(first))) {
            return Optional.of(first.radiationWm2());
        }
        if (!time.isBefore(centerOf(last))) {
            return Optional.of(last.radiationWm2());
        }
        for (int i = 0; i < list.size() - 1; i++) {
            Point before = list.get(i);
            Point after = list.get(i + 1);
            if (time.isBefore(centerOf(after))) {
                double span = Duration.between(centerOf(before), centerOf(after)).toMillis();
                double ratio = Duration.between(centerOf(before), time).toMillis() / span;
                return Optional.of(before.radiationWm2() + (after.radiationWm2() - before.radiationWm2()) * ratio);
            }
        }
        return Optional.of(last.radiationWm2());
    }

    // 그 시각의 예상 태양광 발전량 kW. 예보 범위 밖이면 empty
    public Optional<Double> solarKwAt(LocalDateTime time) {
        return radiationAt(time).map(this::solarKwOf);
    }

    // from이 들어 있는 15분 칸부터 count칸의 예상 (판단 엔진·하루 추천용)
    // 칸 [S, S+15분)의 평균 일사량 = Open-Meteo 시각 S+15분의 값
    public List<SolarForecastSlot> forecastFrom(LocalDateTime from, int count) {
        Map<LocalDateTime, Point> byEnd = new HashMap<>();
        for (Point p : points) {
            byEnd.put(p.end(), p);
        }
        LocalDateTime firstStart = quarterStartOf(from);
        List<SolarForecastSlot> slots = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            LocalDateTime start = firstStart.plus(SLOT.multipliedBy(k));
            Point p = byEnd.get(start.plus(SLOT));
            if (p == null) {
                slots.add(new SolarForecastSlot(start, null, 0, null, null));
            } else {
                slots.add(new SolarForecastSlot(start, p.radiationWm2(), solarKwOf(p.radiationWm2()),
                        p.temperature(), p.cloudCover()));
            }
        }
        return slots;
    }

    // 일사량 → 발전량 kW (소수 1자리)
    double solarKwOf(double radiationWm2) {
        double[] settings = solarSettings();
        double capacityKw = settings[0];
        double kw = radiationWm2 * capacityKw * settings[1] / 1000;
        kw = Math.max(0, Math.min(capacityKw, kw));
        return Math.round(kw * 10) / 10.0;
    }

    // [용량 kW, 성능비]를 setting에서 읽음 (환경 설정에서 바꾸면 바로 반영)
    // 성능비가 80처럼 %로 들어 있으면 0.8로 바꿈
    private double[] solarSettings() {
        double capacityKw = DEFAULT_CAPACITY_KW;
        double ratio = DEFAULT_PERFORMANCE_RATIO;
        for (Setting setting : settingRepository.findAllById(List.of(CAPACITY_KEY, PERFORMANCE_RATIO_KEY))) {
            Double value = parse(setting.getValue());
            if (value == null) {
                continue;
            }
            if (CAPACITY_KEY.equals(setting.getSettingKey())) {
                capacityKw = value;
            } else if (PERFORMANCE_RATIO_KEY.equals(setting.getSettingKey())) {
                ratio = value > 1 ? value / 100 : value;
            }
        }
        return new double[] {capacityKw, ratio};
    }

    private static Double parse(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static Double valueAt(List<Double> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    // 칸 가운데 시각 (end 7분 30초 전)
    private static LocalDateTime centerOf(Point p) {
        return p.end().minus(HALF_SLOT);
    }

    // 시각이 들어 있는 15분 칸의 시작 (예: 14:51 → 14:45)
    private static LocalDateTime quarterStartOf(LocalDateTime time) {
        LocalDateTime hour = time.truncatedTo(ChronoUnit.HOURS);
        return hour.plusMinutes(time.getMinute() / 15 * 15);
    }
}
