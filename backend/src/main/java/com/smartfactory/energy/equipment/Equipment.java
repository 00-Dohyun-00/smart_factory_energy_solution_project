package com.smartfactory.energy.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비 (equipment 테이블). 생산 설비 + 태양광・ESS・한전 계량점까지 모두 여기에 등록합니다.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @Column(name = "equipment_id", length = 30)
    private String equipmentId;

    // zone과 관계(ManyToOne)로 묶지 않고 zoneId 값만 둡니다. (단순하게 유지)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(nullable = false, length = 50)
    private String name;

    // 역할: GRID(한전) / SOLAR(태양광) / ESS / LOAD(생산 설비)
    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_role", nullable = false, length = 10)
    private EquipmentRole equipmentRole;

    @Column(name = "rated_kw", precision = 10, scale = 2)
    private BigDecimal ratedKw;

    @Column(name = "is_virtual", nullable = false)
    private Boolean isVirtual;

    @Column(name = "modbus_slave_id")
    private Integer modbusSlaveId;

    @Column(name = "mqtt_topic", length = 100)
    private String mqttTopic;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Equipment(String equipmentId, Long zoneId, String name, EquipmentRole equipmentRole, BigDecimal ratedKw,
                      Boolean isVirtual, Integer modbusSlaveId, String mqttTopic, Boolean isActive,
                      LocalDateTime createdAt) {
        this.equipmentId = equipmentId;
        this.zoneId = zoneId;
        this.name = name;
        this.equipmentRole = equipmentRole;
        this.ratedKw = ratedKw;
        this.isVirtual = isVirtual;
        this.modbusSlaveId = modbusSlaveId;
        this.mqttTopic = mqttTopic;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }
}
