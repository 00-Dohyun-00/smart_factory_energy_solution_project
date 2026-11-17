package com.smartfactory.energy.simulator;

import com.smartfactory.energy.power.RawFactoryLoad;
import com.smartfactory.energy.power.RawFactoryLoadRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// raw_factory_load 전체(35,040행, 1초 단위)를 처음 한 번만 메모리에 캐싱해두고,
// 호출할 때마다 다음 행을 순서대로 돌려줍니다. 끝까지 가면 처음 행으로 돌아갑니다.
@Component
@RequiredArgsConstructor
public class RawLoadCursor {

    private final RawFactoryLoadRepository rawFactoryLoadRepository;

    private List<RawFactoryLoad> rows;
    private final AtomicInteger index = new AtomicInteger(0);

    public RawFactoryLoad nextRow() {
        if (rows == null) {
            rows = rawFactoryLoadRepository.findAllByOrderByRecordedAtAsc();
        }
        int i = index.getAndUpdate(n -> (n + 1) % rows.size());
        return rows.get(i);
    }
}
