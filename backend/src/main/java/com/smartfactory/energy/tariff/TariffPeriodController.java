package com.smartfactory.energy.tariff;

import com.smartfactory.energy.tariff.dto.TariffPeriodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "요금 시간대", description = "계절별 요금 시간대표 API")
@RestController
@RequestMapping("/api/tariff-periods")
@RequiredArgsConstructor
public class TariffPeriodController {

    private final TariffPeriodService tariffPeriodService;

    @Operation(summary = "요금 시간대표 조회 (season: SUMMER / SPRING_FALL / WINTER, 없으면 전체)")
    @GetMapping // GET /api/tariff-periods?season=WINTER
    public List<TariffPeriodResponse> findAll(@RequestParam(required = false) String season) {
        return tariffPeriodService.findAll(season);
    }
}
