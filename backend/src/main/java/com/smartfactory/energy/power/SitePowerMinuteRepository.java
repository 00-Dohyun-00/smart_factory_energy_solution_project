package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SitePowerMinuteRepository extends JpaRepository<SitePowerMinute, LocalDateTime> {

    // 기간 안의 1분 기록 (start 이상 ~ end 미만, 시각 순)
    @Query("select s from SitePowerMinute s where s.ts >= :start and s.ts < :end order by s.ts")
    List<SitePowerMinute> findInPeriod(LocalDateTime start, LocalDateTime end);
}
