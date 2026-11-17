package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

// 뷰 기반 읽기 전용 Entity. save/delete는 호출하지 않습니다.
public interface DemandInterval15mRepository extends JpaRepository<DemandInterval15m, LocalDateTime> {
}
