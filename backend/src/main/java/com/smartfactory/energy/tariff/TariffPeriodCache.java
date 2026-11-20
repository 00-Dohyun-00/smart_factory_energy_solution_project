package com.smartfactory.energy.tariff;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// tariff_period 전체를 메모리에 캐시합니다. 저장 API는 없지만(화면에서 못 바꿈), 공용 DB라
// 다른 방법으로 값이 바뀔 수 있어 1분마다 다시 읽어서 최대 1분 안에 반영합니다.
@Component
@RequiredArgsConstructor
public class TariffPeriodCache {

    // 유효 시간(캐시가 최대 이만큼 오래될 수 있는 시간)
    private static final long REFRESH_DELAY_MS = 60_000;

    private final TariffPeriodRepository tariffPeriodRepository;

    private volatile List<TariffPeriod> all = List.of();

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = REFRESH_DELAY_MS)
    public void refresh() {
        all = tariffPeriodRepository.findAll();
    }

    public List<TariffPeriod> findAll() {
        return all;
    }

    public List<TariffPeriod> findBySeason(TariffSeason season) {
        return all.stream().filter(p -> p.getSeason() == season).toList();
    }
}
