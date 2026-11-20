package com.smartfactory.energy.power;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 뷰 기반 읽기 전용 Entity. save/delete는 호출하지 않습니다.
public interface DemandInterval15mRepository extends JpaRepository<DemandInterval15m, LocalDateTime> {

    // 기간 안의 15분 구간 (start 이상 ~ end 미만, 시각 순)
    @Query("select d from DemandInterval15m d where d.intervalStart >= :start and d.intervalStart < :end order by d.intervalStart")
    List<DemandInterval15m> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 기간 안에서 15분이 다 찬 구간들 중 최대 실제 평균 kW (SQL MAX, 구간 전체를 가져오지 않음). 없으면 null
    @Query("select max(d.actualAvgKw) from DemandInterval15m d "
            + "where d.intervalStart >= :start and d.intervalStart < :end "
            + "and d.minuteCount >= 15 and d.actualAvgKw is not null")
    BigDecimal maxActualAvgKw(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
