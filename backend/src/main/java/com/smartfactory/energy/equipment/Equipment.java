package com.smartfactory.energy.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비 (equipment 테이블). 생산 설비 + 태양광·ESS·한전 계량점까지 모두 여기에 등록합니다.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    // 설비 ID (예: Z2-FURN). 사람이 정하는 글자라 자동 증가가 아님
    @Id
    @Column(length = 30)
    private String equipmentId;

    // 속한 구역 번호 (zone.zone_id). 태양광·ESS·한전처럼 구역이 없으면 null
    private Long zoneId;

    // 설비 이름 (예: 브레이징로)
    @Column(nullable = false, length = 50)
    private String name;

    // 역할: GRID(한전) / SOLAR(태양광) / ESS / LOAD(생산 설비)
    @Column(nullable = false, length = 10)
    private String equipmentRole;

    // 정격 kW
    @Column(columnDefinition = "numeric")
    private Double ratedKw;

    // 가상 설비면 true (실측 인버터 모터만 false)
    private Boolean isVirtual;

    // Modbus 슬레이브 ID (실측 설비만)
    private Integer modbusSlaveId;

    // MQTT 토픽 (실측 설비만)
    @Column(length = 100)
    private String mqttTopic;

    // 사용 여부
    private Boolean isActive;

    // 등록 시각
    private LocalDateTime createdAt;
}
