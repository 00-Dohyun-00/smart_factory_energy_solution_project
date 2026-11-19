package com.smartfactory.energy.equipment.dto;

import com.smartfactory.energy.equipment.Equipment;
import com.smartfactory.energy.equipment.EquipmentRole;
import java.math.BigDecimal;

public record EquipmentResponse(
        String equipmentId,
        String zoneCode,
        String name,
        EquipmentRole equipmentRole,
        BigDecimal ratedKw
) {

    // Entity -> DTO 변환
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getEquipmentId(),
                equipment.getZoneCode(),
                equipment.getName(),
                equipment.getEquipmentRole(),
                equipment.getRatedKw()
        );
    }
}
