package com.smartfactory.energy.equipment.dto;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EquipmentResponse(
        String equipmentId,
        Long zoneId,
        String name,
        EquipmentRole equipmentRole,
        BigDecimal ratedKw,
        Boolean isVirtual,
        Integer modbusSlaveId,
        String mqttTopic,
        Boolean isActive,
        LocalDateTime createdAt
) {

    // Entity -> DTO 변환
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getEquipmentId(),
                equipment.getZoneId(),
                equipment.getName(),
                equipment.getEquipmentRole(),
                equipment.getRatedKw(),
                equipment.getIsVirtual(),
                equipment.getModbusSlaveId(),
                equipment.getMqttTopic(),
                equipment.getIsActive(),
                equipment.getCreatedAt()
        );
    }
}
