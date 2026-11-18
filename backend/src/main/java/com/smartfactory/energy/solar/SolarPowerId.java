package com.smartfactory.energy.solar;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

// SolarPower의 복합키 (ts + power_type)
public class SolarPowerId implements Serializable {

    private LocalDateTime ts;
    private SolarPowerType powerType;

    public SolarPowerId() {
    }

    public SolarPowerId(LocalDateTime ts, SolarPowerType powerType) {
        this.ts = ts;
        this.powerType = powerType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SolarPowerId that)) {
            return false;
        }
        return Objects.equals(ts, that.ts) && powerType == that.powerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ts, powerType);
    }
}
