package com.smartfactory.energy.zone.dto;

import com.smartfactory.energy.zone.Zone;

public record ZoneResponse(Long zoneId, String zoneCode, String name, Integer displayOrder) {

    // Entity -> DTO 변환
    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getZoneId(), zone.getZoneCode(), zone.getName(), zone.getDisplayOrder());
    }
}
