package com.smartfactory.energy.power;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 뷰 기반 읽기 전용 Entity. save/delete는 호출하지 않습니다.
public interface RawEquipmentLoadRepository extends JpaRepository<RawEquipmentLoad, RawEquipmentLoadId> {

    List<RawEquipmentLoad> findByRecordedAt(LocalDateTime recordedAt);
}
