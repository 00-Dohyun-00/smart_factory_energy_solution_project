package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PowerMinuteRepository extends JpaRepository<PowerMinute, PowerMinuteId> {

    // 한 시각(1분)의 설비별 기록
    List<PowerMinute> findByTs(LocalDateTime ts);

    // 설비 여러 개의 start 이상 ~ end 미만 기록을 시간 순으로 조회
    @Query("select p from PowerMinute p where p.equipmentId in :equipmentIds and p.ts >= :start and p.ts < :end order by p.ts")
    List<PowerMinute> findInPeriod(List<String> equipmentIds, LocalDateTime start, LocalDateTime end);
}
