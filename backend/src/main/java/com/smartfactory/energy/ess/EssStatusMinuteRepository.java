package com.smartfactory.energy.ess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EssStatusMinuteRepository extends JpaRepository<EssStatusMinute, LocalDateTime> {

    // start 이상 ~ end 미만 기간의 1분 기록을 시간 순으로 조회
    @Query("select e from EssStatusMinute e where e.ts >= :start and e.ts < :end order by e.ts")
    List<EssStatusMinute> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 가장 최근 1분 기록 (서버 재시작 때 SOC를 이어서 시작하기 위함)
    Optional<EssStatusMinute> findFirstByOrderByTsDesc();

    // 기간 중 잔량이 가장 낮았던/높았던 1분. 없으면 null
    Optional<EssStatusMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderBySocAsc(LocalDateTime start, LocalDateTime end);

    Optional<EssStatusMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderBySocDesc(LocalDateTime start, LocalDateTime end);

    // 기간 중 배터리실 온도가 가장 높았던 1분. 없으면 null
    Optional<EssStatusMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderByBatteryTempDesc(LocalDateTime start, LocalDateTime end);

    // 기간의 처음·마지막 1분 (효율 계산에 쓰는 "기간 중 저장량 변화" = 마지막 SOC − 처음 SOC)
    Optional<EssStatusMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderByTsAsc(LocalDateTime start, LocalDateTime end);

    Optional<EssStatusMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderByTsDesc(LocalDateTime start, LocalDateTime end);

    // 기간 평균·최저 잔량 (SQL AVG/MIN 1행). 기록이 없으면 둘 다 null
    @Query("select new com.smartfactory.energy.ess.EssSocTotals(avg(e.soc), min(e.soc)) "
            + "from EssStatusMinute e where e.ts >= :start and e.ts < :end")
    EssSocTotals socTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // interval(HOUR/DAY) 단위로 평균 낸 ESS 상태 (date_trunc). 기록이 없는 칸은 나오지 않음
    @Query(value = "select date_trunc(:unit, ts) as bucket, avg(soc) as avg_soc, avg(battery_temp) as avg_battery_temp "
            + "from ess_status_minute where ts >= :start and ts < :end "
            + "group by bucket order by bucket", nativeQuery = true)
    List<EssBucketAverage> averageByUnit(@Param("unit") String unit, @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    // 15분(QUARTER) 단위로 평균 낸 ESS 상태 (date_bin). 기록이 없는 칸은 나오지 않음
    @Query(value = "select date_bin('15 minutes', ts, timestamp '2000-01-01 00:00:00') as bucket, "
            + "avg(soc) as avg_soc, avg(battery_temp) as avg_battery_temp "
            + "from ess_status_minute where ts >= :start and ts < :end "
            + "group by bucket order by bucket", nativeQuery = true)
    List<EssBucketAverage> averageByQuarter(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
