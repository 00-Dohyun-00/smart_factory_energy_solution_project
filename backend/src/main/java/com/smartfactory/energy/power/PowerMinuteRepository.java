package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PowerMinuteRepository extends JpaRepository<PowerMinute, PowerMinuteId> {

    // 한 시각(1분)의 설비별 기록
    List<PowerMinute> findByTs(LocalDateTime ts);
}
