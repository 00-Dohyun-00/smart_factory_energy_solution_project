package com.smartfactory.energy.loadforecast;

import static com.smartfactory.energy.TestData.power;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.loadforecast.dto.AiLoadForecastResponse;
import com.smartfactory.energy.loadforecast.dto.LoadForecast;
import com.smartfactory.energy.power.SitePowerMinute;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

// AI 예측 받기(지난 4칸 오차 보정), AI 서버가 없거나 칸이 빠지면 최근 1시간 평균, 응답 JSON 읽기
@ExtendWith(MockitoExtension.class)
class LoadForecastServiceTest {

    private static final LocalDateTime WINDOW = LocalDateTime.of(2026, 11, 19, 16, 15);

    @Mock AiForecastClient aiForecastClient;
    @InjectMocks LoadForecastService loadForecastService;

    // WINDOW 1시간 전부터 1분 기록 60줄 (부하 kW)
    private static List<SitePowerMinute> history(double loadKw) {
        List<SitePowerMinute> rows = new ArrayList<>();
        for (int i = 60; i >= 1; i--) {
            rows.add(power(WINDOW.minusMinutes(i), loadKw, 0, 0, loadKw));
        }
        return rows;
    }

    // WINDOW 1시간 전부터 count칸, 평균 loadKw / 상위 90% peakKw
    private static AiLoadForecastResponse aiResponse(int count, double loadKw, double peakKw) {
        List<AiLoadForecastResponse.Slot> slots = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            slots.add(new AiLoadForecastResponse.Slot(WINDOW.minusMinutes(60).plusMinutes(15L * k), loadKw, peakKw));
        }
        return new AiLoadForecastResponse("테스트 모델", slots);
    }

    @Test
    void AI_예측을_지난_1시간_오차만큼_옮겨_씀() {
        // AI는 1,500으로 봤는데 지난 1시간 실제는 1,400 → 앞으로도 100 낮춤
        when(aiForecastClient.fetch(WINDOW.minusMinutes(60), 100)).thenReturn(aiResponse(100, 1500, 1600));

        LoadForecast forecast = loadForecastService.forecast(WINDOW, 96, history(1400));

        assertThat(forecast.ai()).isTrue();
        assertThat(forecast.biasKw()).isEqualTo(-100);
        assertThat(forecast.slots()).hasSize(96);
        assertThat(forecast.slots().get(0).slotStart()).isEqualTo(WINDOW);
        assertThat(forecast.slots().get(0).loadKw()).isEqualTo(1400);
        assertThat(forecast.slots().get(0).peakLoadKw()).isEqualTo(1500);
        assertThat(forecast.method()).contains("AI 예측").contains("-100 kW");
    }

    @Test
    void 상위_90퍼센트_예측이_평균보다_낮으면_평균을_씀() {
        when(aiForecastClient.fetch(any(), anyInt())).thenReturn(aiResponse(100, 1500, 1450));

        LoadForecast forecast = loadForecastService.forecast(WINDOW, 96, history(1500));

        assertThat(forecast.slots().get(0).peakLoadKw()).isEqualTo(1500);
    }

    @Test
    void AI_서버가_응답하지_않으면_최근_1시간_평균() {
        when(aiForecastClient.fetch(any(), anyInt())).thenThrow(new ResourceAccessException("연결 거부"));

        LoadForecast forecast = loadForecastService.forecast(WINDOW, 96, history(300));

        verify(aiForecastClient).fetch(any(), anyInt());
        assertThat(forecast.ai()).isFalse();
        assertThat(forecast.method()).isEqualTo(LoadForecastService.FALLBACK_METHOD);
        assertThat(forecast.slots()).hasSize(96).allSatisfy(slot -> assertThat(slot.loadKw()).isEqualTo(300));
    }

    @Test
    void AI_응답에_칸이_모자라면_최근_1시간_평균() {
        when(aiForecastClient.fetch(any(), anyInt())).thenReturn(aiResponse(50, 1500, 1600));

        LoadForecast forecast = loadForecastService.forecast(WINDOW, 96, history(300));

        assertThat(forecast.ai()).isFalse();
    }

    @Test
    void AI_서버_응답_JSON을_읽음() {
        String json = """
                {"model": "HistGradientBoostingRegressor",
                 "slots": [{"slot_start": "2026-11-19T16:15:00", "load_kw": 1712.3, "peak_load_kw": 1800.5}]}
                """;

        AiLoadForecastResponse r = JsonMapper.builder().build().readValue(json, AiLoadForecastResponse.class);

        assertThat(r.model()).isEqualTo("HistGradientBoostingRegressor");
        assertThat(r.slots().get(0).slotStart()).isEqualTo(WINDOW);
        assertThat(r.slots().get(0).loadKw()).isEqualTo(1712.3);
        assertThat(r.slots().get(0).peakLoadKw()).isEqualTo(1800.5);
    }
}
