package com.smartfactory.energy.global.failover;

import com.smartfactory.energy.global.failover.dto.DbStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DB 상태", description = "Supabase 장애/백업 DB 전환 상태 API")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class DbStatusController {

    private static final Set<String> VALID_TARGETS = Set.of("primary", "backup", "auto");

    private final SupabaseHealthChecker healthChecker;

    @Operation(summary = "Supabase 상태 및 현재 사용 중인 DB 조회")
    @GetMapping("/db-status")
    public DbStatusResponse status() {
        return new DbStatusResponse(healthChecker.status(), healthChecker.currentTarget(), healthChecker.lastChanged());
    }

    @Operation(summary = "DB 대상 수동 고정/해제 (primary, backup, auto)")
    @PostMapping("/db-target")
    public DbStatusResponse setTarget(@RequestParam String target) {
        if (!VALID_TARGETS.contains(target)) {
            throw new IllegalArgumentException("target은 primary, backup, auto 중 하나여야 합니다.");
        }
        healthChecker.setManualTarget("auto".equals(target) ? null : target);
        return status();
    }
}
