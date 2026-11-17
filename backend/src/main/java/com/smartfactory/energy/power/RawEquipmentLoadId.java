package com.smartfactory.energy.power;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

// RawEquipmentLoad의 복합키 (recorded_at + equipment_id)
public class RawEquipmentLoadId implements Serializable {

    private LocalDateTime recordedAt;
    private String equipmentId;

    public RawEquipmentLoadId() {
    }

    public RawEquipmentLoadId(LocalDateTime recordedAt, String equipmentId) {
        this.recordedAt = recordedAt;
        this.equipmentId = equipmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RawEquipmentLoadId that)) {
            return false;
        }
        return Objects.equals(recordedAt, that.recordedAt) && Objects.equals(equipmentId, that.equipmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordedAt, equipmentId);
    }
}
