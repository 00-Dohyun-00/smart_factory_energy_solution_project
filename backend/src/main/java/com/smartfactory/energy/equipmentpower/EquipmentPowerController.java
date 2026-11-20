package com.smartfactory.energy.equipmentpower;

import com.smartfactory.energy.equipmentpower.dto.EquipmentPowerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "설비별 전력", description = "15분 구간별 설비 전력 API")
@RestController
@RequestMapping("/api/equipment-powers")
@RequiredArgsConstructor
public class EquipmentPowerController {

    private final EquipmentPowerService equipmentPowerService;

    @Operation(summary = "기간 안의 15분 구간별 · 부하 설비별 평균 kW (최대 24시간)")
    @GetMapping // GET /api/equipment-powers?from=2026-11-19T14:15:00&to=2026-11-19T14:30:00
    public List<EquipmentPowerResponse> findInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return equipmentPowerService.findInPeriod(from, to);
    }
}
