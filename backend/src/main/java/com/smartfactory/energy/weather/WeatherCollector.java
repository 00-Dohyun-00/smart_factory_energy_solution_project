package com.smartfactory.energy.weather;

import com.smartfactory.energy.weather.dto.OpenMeteoResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 15분마다 Open-Meteo에서 날씨를 받아서
 * 1) 지금 15분 칸의 날씨를 weather 테이블에 1줄 저장하고 (분석 화면·실시간 화면 날씨)
 * 2) 앞으로 24시간 예보를 SolarForecastService에 넘깁니다. (태양광 발전량 계산)
 *
 * 공용 DB라서 한 대만 받아야 하므로 시뮬레이터를 켠 서버(simulator.enabled=true)에서만 돕니다.
 * 시뮬레이터가 이 예보로 태양광 발전량을 만들기 때문에 같은 서버에 두었습니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true")
@RequiredArgsConstructor
public class WeatherCollector {

    private final OpenMeteoClient openMeteoClient;
    private final SolarForecastService solarForecastService;
    private final WeatherRepository weatherRepository;

    // 서버를 켜자마자 한 번 받음 (다음 15분까지 태양광이 0으로 나오지 않게)
    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStart() {
        collect();
    }

    // 15분 칸이 바뀌고 2분 뒤 (Open-Meteo가 새 칸을 반영할 여유)
    @Scheduled(cron = "0 2,17,32,47 * * * *", zone = "Asia/Seoul")
    public void collect() {
        OpenMeteoResponse response;
        try {
            response = openMeteoClient.fetch();
        } catch (RuntimeException e) {
            // 다시 시도는 다음 15분 수집에 맡김. 그동안 태양광은 이전 예보로 계속 계산됨
            log.warn("Open-Meteo 날씨 받기 실패 (다음 15분에 다시 받음): {}", e.getMessage());
            return;
        }
        if (response == null) {
            log.warn("Open-Meteo 응답이 비어 있음");
            return;
        }
        solarForecastService.update(response);
        try {
            saveCurrent(response.current());
        } catch (RuntimeException e) {
            log.warn("날씨 저장 실패: {}", e.getMessage());
        }
    }

    // 지금 15분 칸을 weather에 1줄 저장. 같은 칸이 이미 있으면 그대로 둠 (두 번 받아도 1줄)
    void saveCurrent(OpenMeteoResponse.Current current) {
        if (current == null || current.time() == null) {
            return;
        }
        if (weatherRepository.existsById(current.time())) {
            return;
        }
        weatherRepository.save(new Weather(
                current.time(),
                decimal(current.temperature()),
                decimal(current.humidity()),
                decimal(current.cloudCover()),
                skyOf(current.cloudCover()),
                decimal(current.radiationWm2()),
                WeatherSource.OPEN_METEO,
                decimal(current.precipitation())
        ));
    }

    // 운량 % → 하늘 상태 (기상청 기준 0~5할 맑음, 6~8할 구름많음, 9~10할 흐림을 %로 바꾼 값)
    // 실시간 화면·분석 화면이 sky 칸을 읽어서, 2025년 기록처럼 같이 저장해 둠
    static Sky skyOf(Double cloudCover) {
        if (cloudCover == null) {
            return null;
        }
        if (cloudCover <= 50) {
            return Sky.CLEAR;
        }
        if (cloudCover <= 80) {
            return Sky.CLOUDY;
        }
        return Sky.OVERCAST;
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
