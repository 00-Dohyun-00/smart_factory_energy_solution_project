package com.smartfactory.energy.loadforecast;

import com.smartfactory.energy.loadforecast.dto.AiLoadForecastResponse;
import com.smartfactory.energy.loadforecast.dto.LoadForecast;
import com.smartfactory.energy.power.SitePowerMinute;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판단 엔진에 넘길 공장 부하 예측.
 * 1) AI 서버(FastAPI) 예측을 받아서, 최근 1시간(4칸) 동안 빗나간 평균만큼 옮겨 씀
 *    (예: 실제가 예측보다 평균 80 kW 높았으면 앞으로의 칸도 80 kW 올림. 프론트 planInputsOf와 같은 보정)
 * 2) AI 서버가 응답하지 않으면 최근 1시간 평균을 앞으로 24시간에 그대로 씀 (비AI 예측)
 * 판단(충전·방전)은 여기서 하지 않습니다. 예측만 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoadForecastService {

    // 오차 보정에 쓰는 지난 칸 수 (15분 × 4 = 1시간)
    static final int BIAS_SLOTS = 4;
    // 지난 칸의 실제 값으로 인정할 최소 1분 기록 수 (시뮬레이터를 막 켜서 기록이 적은 칸은 보정에서 뺌)
    private static final int MIN_MINUTES_PER_SLOT = 10;
    static final String FALLBACK_METHOD = "최근 1시간 평균 (AI 서버 응답 없음)";

    private final AiForecastClient aiForecastClient;

    // windowStart(지금 15분 칸)부터 count칸. history: 최근 1분 기록 (시간 순)
    public LoadForecast forecast(LocalDateTime windowStart, int count, List<SitePowerMinute> history) {
        try {
            AiLoadForecastResponse response = aiForecastClient.fetch(windowStart.minusMinutes(15L * BIAS_SLOTS), count + BIAS_SLOTS);
            LoadForecast forecast = fromAi(response, windowStart, count, history);
            if (forecast != null) {
                return forecast;
            }
            log.warn("AI 부하 예측 응답에 빠진 칸이 있어 최근 1시간 평균으로 대신함");
        } catch (RuntimeException e) {
            log.warn("AI 서버 부하 예측 실패, 최근 1시간 평균으로 대신함: {}", e.getMessage());
        }
        return fallback(windowStart, count, history);
    }

    // AI 예측 + 최근 오차 보정. 필요한 칸이 빠져 있으면 null
    LoadForecast fromAi(AiLoadForecastResponse response, LocalDateTime windowStart, int count,
                        List<SitePowerMinute> history) {
        if (response == null || response.slots() == null) {
            return null;
        }
        Map<LocalDateTime, AiLoadForecastResponse.Slot> byStart = new HashMap<>();
        for (AiLoadForecastResponse.Slot slot : response.slots()) {
            if (slot.slotStart() != null && slot.loadKw() != null) {
                byStart.put(slot.slotStart(), slot);
            }
        }

        // 지난 4칸: (실제 평균 − 예측) 의 평균 = 보정량
        Map<LocalDateTime, List<Double>> actualBySlot = new HashMap<>();
        for (SitePowerMinute row : history) {
            actualBySlot.computeIfAbsent(quarterStartOf(row.getTs()), key -> new ArrayList<>())
                    .add(row.getLoadKw().doubleValue());
        }
        double errorSum = 0;
        int errorCount = 0;
        for (int k = 1; k <= BIAS_SLOTS; k++) {
            LocalDateTime start = windowStart.minusMinutes(15L * k);
            List<Double> actual = actualBySlot.get(start);
            AiLoadForecastResponse.Slot predicted = byStart.get(start);
            if (actual == null || actual.size() < MIN_MINUTES_PER_SLOT || predicted == null) {
                continue;
            }
            errorSum += average(actual) - predicted.loadKw();
            errorCount++;
        }
        double biasKw = errorCount > 0 ? errorSum / errorCount : 0;

        // 앞으로의 칸: 예측 + 보정량 (0 아래로는 내리지 않음)
        List<LoadForecast.Slot> slots = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            LocalDateTime start = windowStart.plusMinutes(15L * k);
            AiLoadForecastResponse.Slot predicted = byStart.get(start);
            if (predicted == null) {
                return null;
            }
            double loadKw = Math.max(0, predicted.loadKw() + biasKw);
            double peakKw = predicted.peakLoadKw() != null ? predicted.peakLoadKw() + biasKw : loadKw;
            slots.add(new LoadForecast.Slot(start, loadKw, Math.max(loadKw, peakKw)));
        }
        String method = "AI 예측 (" + response.model() + ", 최근 1시간 오차 보정 "
                + String.format("%+,d", Math.round(biasKw)) + " kW)";
        return new LoadForecast(method, true, biasKw, slots);
    }

    // 비AI 예측: 최근 1시간 평균을 앞으로 모든 칸에 그대로 씀
    LoadForecast fallback(LocalDateTime windowStart, int count, List<SitePowerMinute> history) {
        double mean = history.stream().mapToDouble(r -> r.getLoadKw().doubleValue()).average().orElse(0);
        List<LoadForecast.Slot> slots = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            slots.add(new LoadForecast.Slot(windowStart.plusMinutes(15L * k), mean, mean));
        }
        return new LoadForecast(FALLBACK_METHOD, false, 0, slots);
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    // 시각이 들어 있는 15분 칸의 시작 (예: 14:51 → 14:45)
    private static LocalDateTime quarterStartOf(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.HOURS).plusMinutes(time.getMinute() / 15 * 15);
    }
}
