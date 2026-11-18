package com.smartfactory.energy.tariff;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffPeriodRepository extends JpaRepository<TariffPeriod, Long> {

    // 한 계절의 시간대 목록 (예: WINTER → 겨울의 경부하·중간·최대 시간대들)
    List<TariffPeriod> findBySeason(TariffSeason season);
}
