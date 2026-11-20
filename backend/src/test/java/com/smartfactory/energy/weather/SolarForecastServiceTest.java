package com.smartfactory.energy.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.setting.SettingCache;
import com.smartfactory.energy.weather.dto.OpenMeteoResponse;
import com.smartfactory.energy.weather.dto.SolarForecastSlot;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

// 예보 응답 읽기, 칸 사이 일사량 잇기, 발전량 계산(설정값·자르기), 판단 엔진용 15분 칸 맞추기
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolarForecastServiceTest {

    // 실제 Open-Meteo 응답 모양을 줄인 것 (14:51에 받은 응답. 지금 칸 14:45부터 15분 간격)
    private static final String RESPONSE_JSON = """
            {
              "latitude": 35.2, "longitude": 128.625, "timezone": "Asia/Seoul",
              "current": {"time": "2026-11-19T14:45", "interval": 900, "temperature_2m": 8.3,
                          "relative_humidity_2m": 59, "cloud_cover": 82, "shortwave_radiation": 131.0,
                          "precipitation": 0.0},
              "minutely_15": {
                "time": ["2026-11-19T14:45", "2026-11-19T15:00", "2026-11-19T15:15", "2026-11-19T15:30"],
                "shortwave_radiation": [130.0, 116.0, 104.0, null],
                "temperature_2m": [8.3, 8.1, 8.0, 7.8],
                "cloud_cover": [82, 83, 79, 72]
              }
            }
            """;

    @Mock SettingCache settingCache;
    @InjectMocks SolarForecastService solarForecastService;

    @BeforeEach
    void setUp() {
        // Mockito가 안 정해진 Double 반환을 0.0으로 주는 것 때문에 "설정값 없음"이 0으로 보이지 않게 null로 둠
        when(settingCache.getNumber(anyString())).thenReturn(null);
    }

    private static OpenMeteoResponse response() {
        return JsonMapper.builder().build().readValue(RESPONSE_JSON, OpenMeteoResponse.class);
    }

    private static LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(2026, 11, 19, hour, minute, second);
    }

    @Test
    void 응답을_읽어서_시각과_값을_꺼냄() {
        OpenMeteoResponse r = response();

        assertThat(r.current().time()).isEqualTo(at(14, 45, 0));
        assertThat(r.current().temperature()).isEqualTo(8.3);
        assertThat(r.current().humidity()).isEqualTo(59.0);
        assertThat(r.current().radiationWm2()).isEqualTo(131.0);
        assertThat(r.minutely15().time()).hasSize(4);
        assertThat(r.minutely15().radiationWm2().get(3)).isNull();
    }

    @Test
    void 예보가_없으면_발전량도_없음() {
        assertThat(solarForecastService.solarKwAt(at(14, 50, 0))).isEmpty();
        assertThat(solarForecastService.fetchedAt()).isNull();
    }

    @Test
    void 칸_가운데_시각에는_그_칸_값_사이는_직선으로_이음() {
        solarForecastService.update(response());

        // 15:00 값(116)은 14:45~15:00 평균 → 가운데 14:52:30에 둠
        assertThat(solarForecastService.radiationAt(at(14, 52, 30))).hasValue(116.0);
        // 14:52:30(116)과 15:07:30(104)의 딱 가운데 = 110
        assertThat(solarForecastService.radiationAt(at(15, 0, 0))).hasValue(110.0);
        assertThat(solarForecastService.fetchedAt()).isEqualTo(at(14, 45, 0));
    }

    @Test
    void 예보_범위_밖이면_비어_있음() {
        solarForecastService.update(response());

        // 일사량이 null인 15:30 칸은 빠져서, 마지막 값은 15:15(15:00~15:15 칸)까지
        assertThat(solarForecastService.radiationAt(at(15, 15, 0))).isEmpty();
        assertThat(solarForecastService.radiationAt(at(14, 29, 59))).isEmpty();
        // 첫 칸(14:30~14:45) 앞쪽 절반은 첫 값 그대로
        assertThat(solarForecastService.radiationAt(at(14, 31, 0))).hasValue(130.0);
    }

    @Test
    void 발전량은_일사량_곱하기_용량_곱하기_성능비() {
        // 기본값: 1,000 kW × 0.8 → 일사량 116이면 92.8 kW
        assertThat(solarForecastService.solarKwOf(116)).isEqualTo(92.8);

        when(settingCache.getNumber("solar.capacity_kw")).thenReturn(500.0);
        when(settingCache.getNumber("solar.performance_ratio")).thenReturn(80.0); // %로 들어 있어도 0.8로 봄
        assertThat(solarForecastService.solarKwOf(116)).isEqualTo(46.4);
    }

    @Test
    void 발전량은_0과_용량_사이로_자름() {
        assertThat(solarForecastService.solarKwOf(2000)).isEqualTo(1000.0);
        assertThat(solarForecastService.solarKwOf(-5)).isEqualTo(0.0);
    }

    @Test
    void 판단_엔진용_칸은_칸이_끝나는_시각의_값을_씀() {
        solarForecastService.update(response());

        List<SolarForecastSlot> slots = solarForecastService.forecastFrom(at(14, 51, 0), 3);

        // 14:45~15:00 칸 = Open-Meteo 15:00 값(116)
        assertThat(slots.get(0).slotStart()).isEqualTo(at(14, 45, 0));
        assertThat(slots.get(0).radiationWm2()).isEqualTo(116.0);
        assertThat(slots.get(0).solarKw()).isEqualTo(92.8);
        assertThat(slots.get(0).temperatureC()).isEqualTo(8.1);
        assertThat(slots.get(1).radiationWm2()).isEqualTo(104.0);
        // 예보가 없는 칸은 발전량 0
        assertThat(slots.get(2).radiationWm2()).isNull();
        assertThat(slots.get(2).solarKw()).isEqualTo(0.0);
    }
}
