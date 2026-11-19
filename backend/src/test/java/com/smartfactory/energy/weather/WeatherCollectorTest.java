package com.smartfactory.energy.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.weather.dto.OpenMeteoResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

// 지금 날씨 저장(같은 칸은 1줄만), 운량 → 하늘 상태, 받기 실패해도 멈추지 않음
@ExtendWith(MockitoExtension.class)
class WeatherCollectorTest {

    private static final LocalDateTime STEP = LocalDateTime.of(2026, 11, 19, 14, 45);

    @Mock OpenMeteoClient openMeteoClient;
    @Mock SolarForecastService solarForecastService;
    @Mock WeatherRepository weatherRepository;
    @InjectMocks WeatherCollector weatherCollector;

    private static OpenMeteoResponse response() {
        OpenMeteoResponse.Current current = new OpenMeteoResponse.Current(STEP, 8.3, 59.0, 82.0, 131.0, 0.0);
        OpenMeteoResponse.Minutely15 minutely = new OpenMeteoResponse.Minutely15(
                List.of(STEP), List.of(130.0), List.of(8.3), List.of(82.0));
        return new OpenMeteoResponse(current, minutely);
    }

    @Test
    void 받은_날씨를_저장하고_예보를_넘김() {
        OpenMeteoResponse response = response();
        when(openMeteoClient.fetch()).thenReturn(response);
        when(weatherRepository.existsById(STEP)).thenReturn(false);

        weatherCollector.collect();

        verify(solarForecastService).update(response);
        ArgumentCaptor<Weather> saved = ArgumentCaptor.forClass(Weather.class);
        verify(weatherRepository).save(saved.capture());
        Weather w = saved.getValue();
        assertThat(w.getTs()).isEqualTo(STEP);
        assertThat(w.getSource()).isEqualTo(WeatherSource.OPEN_METEO);
        assertThat(w.getTemperature()).isEqualByComparingTo(new BigDecimal("8.3"));
        assertThat(w.getRadiationWm2()).isEqualByComparingTo(new BigDecimal("131"));
        assertThat(w.getSky()).isEqualTo(Sky.OVERCAST);
    }

    @Test
    void 같은_칸이_이미_있으면_저장하지_않음() {
        when(openMeteoClient.fetch()).thenReturn(response());
        when(weatherRepository.existsById(STEP)).thenReturn(true);

        weatherCollector.collect();

        verify(weatherRepository, never()).save(any());
    }

    @Test
    void 받기에_실패하면_아무것도_바꾸지_않음() {
        when(openMeteoClient.fetch()).thenThrow(new ResourceAccessException("시간 초과"));

        weatherCollector.collect();

        verify(solarForecastService, never()).update(any());
        verify(weatherRepository, never()).save(any());
    }

    @Test
    void 운량으로_하늘_상태를_정함() {
        assertThat(WeatherCollector.skyOf(50.0)).isEqualTo(Sky.CLEAR);
        assertThat(WeatherCollector.skyOf(51.0)).isEqualTo(Sky.CLOUDY);
        assertThat(WeatherCollector.skyOf(80.0)).isEqualTo(Sky.CLOUDY);
        assertThat(WeatherCollector.skyOf(81.0)).isEqualTo(Sky.OVERCAST);
        assertThat(WeatherCollector.skyOf(null)).isNull();
    }
}
