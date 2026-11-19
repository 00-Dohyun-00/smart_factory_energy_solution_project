package com.smartfactory.energy.simulator;

import com.smartfactory.energy.power.RawFactoryLoad;
import com.smartfactory.energy.power.RawFactoryLoadRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// raw_factory_load 전체(35,040행, 2026-01-01 00:00:00부터 1초 단위)를 처음 한 번만 메모리에 캐싱해두고,
// 실제 "지금" 시각을 기준으로 재생할 행을 고정된 규칙(시각 mod 전체 행 수)으로 고릅니다.
// 내부 카운터가 아니라 시각 자체로 계산하므로, 서버를 다시 켜도 같은 시각엔 항상 같은 행을 재생합니다.
@Component
@RequiredArgsConstructor
public class RawLoadCursor {

    private static final LocalDateTime CYCLE_START = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final RawFactoryLoadRepository rawFactoryLoadRepository;
    private final SimClock simClock;

    private List<RawFactoryLoad> rows;

    public RawFactoryLoad nextRow() {
        if (rows == null) {
            rows = rawFactoryLoadRepository.findAllByOrderByRecordedAtAsc();
        }
        long elapsedSeconds = Duration.between(CYCLE_START, simClock.now()).getSeconds();
        int index = (int) Math.floorMod(elapsedSeconds, (long) rows.size());
        return rows.get(index);
    }
}
