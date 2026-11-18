package com.smartfactory.energy.global.failover.dto;

import java.time.LocalDateTime;

public record DbStatusResponse(String supabase, String target, LocalDateTime since) {
}
