package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// raw_equipment_load 뷰(view)를 읽기 전용으로 매핑합니다.
// @Subselect를 쓰면 Hibernate가 이 Entity를 실제 테이블로 보지 않아서,
// ddl-auto=update가 이 뷰에 ALTER TABLE을 시도하지 않습니다. (@Table 대신 사용)
@Entity
@Immutable
@Subselect("SELECT recorded_at, equipment_id, kw, is_running FROM raw_equipment_load")
@IdClass(RawEquipmentLoadId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawEquipmentLoad {

    @Id
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Id
    @Column(name = "equipment_id")
    private String equipmentId;

    private BigDecimal kw;

    @Column(name = "is_running")
    private Integer isRunning;
}
