package com.smartfactory.energy.weather;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeatherRepository extends JpaRepository<Weather, LocalDateTime> {

    // start 이상 ~ end 미만 기간의 날씨를 시간 순으로 조회
    @Query("select w from Weather w where w.ts >= :start and w.ts < :end order by w.ts")
    List<Weather> findInPeriod(LocalDateTime start, LocalDateTime end);
}
