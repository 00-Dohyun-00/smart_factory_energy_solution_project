package com.smartfactory.energy.setting;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// setting 전체를 메모리에 캐시합니다. 서버 시작 때 한 번 읽고, 그 뒤로는
// 1) SettingService.update()가 값을 바꾼 그 서버는 바로 다시 읽고,
// 2) 그 밖의 서버(공용 DB를 함께 쓰는 다른 PC)는 1분마다 다시 읽어서 최대 1분 안에 반영합니다.
// 매초(PowerSimulator)·매분(RecommendationEngine 등) 도는 작업이 설정값을 볼 때 이 캐시만 봅니다.
@Component
@RequiredArgsConstructor
public class SettingCache {

    // 유효 시간(캐시가 최대 이만큼 오래될 수 있는 시간). 다른 서버가 바꾼 값은 이 주기로만 따라잡음
    private static final long REFRESH_DELAY_MS = 60_000;

    private final SettingRepository settingRepository;

    private volatile Map<String, String> values = Map.of();

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = REFRESH_DELAY_MS)
    public void refresh() {
        Map<String, String> next = new HashMap<>();
        for (Setting setting : settingRepository.findAll()) {
            next.put(setting.getSettingKey(), setting.getValue());
        }
        values = next;
    }

    // 문자열 그대로. 없으면 null
    public String get(String key) {
        return values.get(key);
    }

    // 전체 (키 → 값). RecommendationEngine처럼 여러 키를 한 번에 봐야 할 때 씀
    public Map<String, String> values() {
        return values;
    }

    // 숫자로 읽음. 없거나 숫자가 아니면 null
    public Double getNumber(String key) {
        String value = values.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 숫자로 읽되, 없으면 기본값
    public double getNumber(String key, double defaultValue) {
        Double value = getNumber(key);
        return value != null ? value : defaultValue;
    }
}
