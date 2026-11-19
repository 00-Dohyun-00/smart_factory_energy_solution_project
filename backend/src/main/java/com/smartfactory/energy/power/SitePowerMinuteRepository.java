package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SitePowerMinuteRepository extends JpaRepository<SitePowerMinute, LocalDateTime> {

    // start 이상 ~ end 미만 기간의 1분 기록을 시간 순으로 조회
    @Query("select s from SitePowerMinute s where s.ts >= :start and s.ts < :end order by s.ts")
    List<SitePowerMinute> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 가장 최근 1분 기록
    Optional<SitePowerMinute> findFirstByOrderByTsDesc();
}
