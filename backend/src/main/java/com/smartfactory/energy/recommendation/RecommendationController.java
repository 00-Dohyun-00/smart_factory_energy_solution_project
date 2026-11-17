package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "추천", description = "ESS 충전/방전 추천 API")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "기간 안의 추천 이력 조회 (최신 순)")
    @GetMapping // GET /api/recommendations?from=2026-11-14&to=2026-11-15
    public List<RecommendationResponse> findInPeriod(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return recommendationService.findInPeriod(from, to);
    }
}
