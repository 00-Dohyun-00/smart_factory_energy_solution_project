package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// 뷰 기반 읽기 전용 Entity. save/delete는 호출하지 않습니다.
public interface DemandInterval15mRepository extends JpaRepository<DemandInterval15m, LocalDateTime> {

    // 기간 안의 15분 구간 (start 이상 ~ end 미만, 시각 순)
    @Query("select d from DemandInterval15m d where d.intervalStart >= :start and d.intervalStart < :end order by d.intervalStart")
    List<DemandInterval15m> findInPeriod(LocalDateTime start, LocalDateTime end);
}
