package com.smartfactory.energy.simulator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// 1분(60초)이 모일 때까지 설비별 초 단위 kW 표본을 메모리에 들고 있습니다.
// @Scheduled 작업은 기본적으로 스레드 1개에서 순서대로 실행되므로 별도 동기화 처리는 하지 않습니다.
@Component
public class MinuteBuffer {

    private final Map<String, List<BigDecimal>> samples = new LinkedHashMap<>();

    public void addSample(String equipmentId, BigDecimal kw) {
        samples.computeIfAbsent(equipmentId, key -> new ArrayList<>()).add(kw);
    }

    public List<BigDecimal> samplesOf(String equipmentId) {
        return samples.getOrDefault(equipmentId, List.of());
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public void clear() {
        samples.clear();
    }
}
