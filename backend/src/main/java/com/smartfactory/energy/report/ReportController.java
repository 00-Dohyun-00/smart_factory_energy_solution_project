package com.smartfactory.energy.report;

import com.smartfactory.energy.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "보고서", description = "월간 성적표 API")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "월간 성적표 조회 (month 예: 2026-11)")
    @GetMapping // GET /api/reports?month=2026-11
    public ReportResponse getReport(@RequestParam String month) {
        return reportService.getReport(month);
    }
}
