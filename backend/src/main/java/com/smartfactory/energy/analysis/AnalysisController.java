package com.smartfactory.energy.analysis;

import com.smartfactory.energy.analysis.dto.TrendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "분석", description = "전력 분석 API")
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @Operation(summary = "기간 추이 분석 (interval: QUARTER=15분, HOUR=1시간, DAY=1일)")
    @GetMapping("/trend") // GET /api/analyses/trend?from=2026-11-10&to=2026-11-16&interval=HOUR
    public TrendResponse getTrend(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "HOUR") String interval) {
        return analysisService.getTrend(from, to, interval);
    }
}
