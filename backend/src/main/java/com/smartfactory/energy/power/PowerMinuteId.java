package com.smartfactory.energy.power;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

// PowerMinute의 복합키 (equipment_id + ts). 필드 이름이 PowerMinute의 @Id 필드와 같아야 합니다.
public class PowerMinuteId implements Serializable {

    private String equipmentId;
    private LocalDateTime ts;

    public PowerMinuteId() {
    }

    public PowerMinuteId(String equipmentId, LocalDateTime ts) {
        this.equipmentId = equipmentId;
        this.ts = ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PowerMinuteId that)) {
            return false;
        }
        return Objects.equals(equipmentId, that.equipmentId) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(equipmentId, ts);
    }
}
