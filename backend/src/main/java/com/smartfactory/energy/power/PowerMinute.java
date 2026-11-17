package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비별 1분 전력 (power_minute 테이블). 설비 1개 × 1분 = 1줄.
 */
@Entity
@Table(name = "power_minute")
@IdClass(PowerMinuteId.class) // 기본키가 equipmentId + ts 두 칸
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PowerMinute {

    // 설비 ID (equipment.equipment_id)
    @Id
    @Column(length = 30)
    private String equipmentId;

    // 측정 시각
    @Id
    private LocalDateTime ts;

    // 1분 평균 kW
    @Column(columnDefinition = "numeric")
    private Double avgKw;

    // 1분 중 최대 kW
    @Column(columnDefinition = "numeric")
    private Double maxKw;

    // 1분 중 최소 kW
    @Column(columnDefinition = "numeric")
    private Double minKw;

    // 1분 사용 전력량 kWh
    @Column(columnDefinition = "numeric")
    private Double energyKwh;

    // 그중 한전 몫 kWh
    @Column(columnDefinition = "numeric")
    private Double gridKwh;

    // 그중 태양광 몫 kWh
    @Column(columnDefinition = "numeric")
    private Double solarKwh;

    // 그중 ESS 몫 kWh
    @Column(columnDefinition = "numeric")
    private Double essKwh;

    // 가동 여부 (1 가동 / 0 정지)
    private Short isRunning;
}
