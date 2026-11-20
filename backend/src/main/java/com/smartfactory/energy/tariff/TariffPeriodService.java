package com.smartfactory.energy.tariff;

import com.smartfactory.energy.tariff.dto.TariffPeriodResponse;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TariffPeriodService {

    private final TariffPeriodCache tariffPeriodCache;

    // 요금 시간대표 조회. season이 없으면 전체, 있으면 그 계절만 (계절 → 시작 시각 순)
    public List<TariffPeriodResponse> findAll(String season) {
        List<TariffPeriod> periods;
        if (season == null || season.isBlank()) {
            periods = tariffPeriodCache.findAll();
        } else {
            periods = tariffPeriodCache.findBySeason(toSeason(season));
        }

        return periods.stream()
                .sorted(Comparator.comparing(TariffPeriod::getSeason).thenComparing(TariffPeriod::getStartTime))
                .map(TariffPeriodResponse::from)
                .toList();
    }

    // "WINTER" → TariffSeason.WINTER. 틀린 값이면 400
    private TariffSeason toSeason(String season) {
        for (TariffSeason value : TariffSeason.values()) {
            if (value.name().equals(season.trim().toUpperCase())) {
                return value;
            }
        }
        throw new IllegalArgumentException("season은 SUMMER, SPRING_FALL, WINTER 중 하나여야 합니다.");
    }
}
