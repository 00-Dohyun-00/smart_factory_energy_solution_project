package com.smartfactory.energy.zone;

import com.smartfactory.energy.zone.dto.ZoneResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구역", description = "구역(Zone) 조회 API")
@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @Operation(summary = "구역 목록 조회 (zone_code 순서)")
    @GetMapping
    public List<ZoneResponse> findAll() {
        return zoneService.findAll();
    }
}
