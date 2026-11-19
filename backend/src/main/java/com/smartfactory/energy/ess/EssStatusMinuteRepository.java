package com.smartfactory.energy.ess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EssStatusMinuteRepository extends JpaRepository<EssStatusMinute, LocalDateTime> {

    // start 이상 ~ end 미만 기간의 1분 기록을 시간 순으로 조회
    @Query("select e from EssStatusMinute e where e.ts >= :start and e.ts < :end order by e.ts")
    List<EssStatusMinute> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 가장 최근 1분 기록
    Optional<EssStatusMinute> findFirstByOrderByTsDesc();
}
