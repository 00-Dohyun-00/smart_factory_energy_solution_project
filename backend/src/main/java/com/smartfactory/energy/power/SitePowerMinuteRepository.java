package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SitePowerMinuteRepository extends JpaRepository<SitePowerMinute, LocalDateTime> {

    // start 이상 ~ end 미만 기간의 1분 기록을 시간 순으로 조회
    @Query("select s from SitePowerMinute s where s.ts >= :start and s.ts < :end order by s.ts")
    List<SitePowerMinute> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 가장 최근 1분 기록
    Optional<SitePowerMinute> findFirstByOrderByTsDesc();

    // 기간의 grid/solar 합과 ESS 충·방전 합을 SQL이 더해서 1행으로 줌
    // (예: "오늘 누적" 계산에 하루치 1,440행을 그대로 가져오지 않고 SUM 결과 1행만 받음)
    @Query("select new com.smartfactory.energy.power.EnergyTotals("
            + "sum(s.gridKw), sum(s.solarKw), "
            + "sum(case when s.essKw > 0 then s.essKw else 0 end), "
            + "sum(case when s.essKw < 0 then -s.essKw else 0 end)) "
            + "from SitePowerMinute s where s.ts >= :start and s.ts < :end")
    EnergyTotals sumEnergy(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 가장 작은 한전 구매 1분. 없으면 null
    Optional<SitePowerMinute> findFirstByTsGreaterThanEqualAndTsLessThanOrderByGridKwAsc(LocalDateTime start, LocalDateTime end);

    // 한낮(12시대)에 태양광이 가장 적었던 1분. 없으면 null
    @Query(value = "select * from site_power_minute "
            + "where ts >= :start and ts < :end and extract(hour from ts) = 12 "
            + "order by solar_kw asc limit 1", nativeQuery = true)
    Optional<SitePowerMinute> findMinSolarAtNoon(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 기간 요약(평균 한전 구매, 태양광·공장 소비·ESS 충방전 합)을 SQL이 구해서 1행으로 줌
    @Query("select new com.smartfactory.energy.power.PowerSummaryTotals("
            + "avg(s.gridKw), sum(s.loadKw), sum(s.solarKw), "
            + "sum(case when s.essKw > 0 then s.essKw else 0 end), "
            + "sum(case when s.essKw < 0 then -s.essKw else 0 end)) "
            + "from SitePowerMinute s where s.ts >= :start and s.ts < :end")
    PowerSummaryTotals summaryTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // interval(HOUR/DAY) 단위로 평균 낸 점 목록 (date_trunc). 기록이 없는 칸은 나오지 않음
    @Query(value = "select date_trunc(:unit, ts) as bucket, avg(load_kw) as avg_load_kw, "
            + "avg(grid_kw) as avg_grid_kw, avg(solar_kw) as avg_solar_kw, avg(ess_kw) as avg_ess_kw "
            + "from site_power_minute where ts >= :start and ts < :end "
            + "group by bucket order by bucket", nativeQuery = true)
    List<PowerBucketAverage> averageByUnit(@Param("unit") String unit, @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    // 15분(QUARTER) 단위로 평균 낸 점 목록 (date_bin). 기록이 없는 칸은 나오지 않음
    @Query(value = "select date_bin('15 minutes', ts, timestamp '2000-01-01 00:00:00') as bucket, "
            + "avg(load_kw) as avg_load_kw, avg(grid_kw) as avg_grid_kw, "
            + "avg(solar_kw) as avg_solar_kw, avg(ess_kw) as avg_ess_kw "
            + "from site_power_minute where ts >= :start and ts < :end "
            + "group by bucket order by bucket", nativeQuery = true)
    List<PowerBucketAverage> averageByQuarter(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 시각(0~23)마다 평균 한전 구매 (여러 날짜를 하나의 시각으로 묶음)
    @Query(value = "select extract(hour from ts)::int as hour, avg(grid_kw) as avg_grid_kw "
            + "from site_power_minute where ts >= :start and ts < :end "
            + "group by hour order by hour", nativeQuery = true)
    List<HourlyGridAverage> averageByHourOfDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 1시간 단위 전력 합계 (성적표 전기요금·ESS·태양광 계산용). 기록이 없는 시간은 나오지 않음
    @Query(value = "select date_trunc('hour', ts) as bucket, count(*) as row_count, "
            + "sum(case when grid_kw > 0 then grid_kw else 0 end) as grid_kw_sum, "
            + "sum(solar_kw) as solar_kw_sum, "
            + "sum(case when ess_kw > 0 then ess_kw else 0 end) as discharge_kw_sum, "
            + "sum(case when ess_kw < 0 then -ess_kw else 0 end) as charge_kw_sum, "
            + "sum(least(solar_kw, load_kw)) as min_solar_load_kw_sum "
            + "from site_power_minute where ts >= :start and ts < :end "
            + "group by bucket order by bucket", nativeQuery = true)
    List<HourlyPowerTotals> hourlyTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
