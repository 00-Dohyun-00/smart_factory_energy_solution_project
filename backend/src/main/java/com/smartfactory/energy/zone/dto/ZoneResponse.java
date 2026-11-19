package com.smartfactory.energy.zone.dto;

import com.smartfactory.energy.zone.Zone;

public record ZoneResponse(String zoneCode, String name) {

    // Entity -> DTO 변환
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getZoneCode(), zone.getName());
    }
}
