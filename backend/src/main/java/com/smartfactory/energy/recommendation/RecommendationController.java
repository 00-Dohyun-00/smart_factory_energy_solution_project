package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import com.smartfactory.energy.recommendation.dto.RecommendationStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "지금 추천 1건 조회 (추천이 없으면 204)")
    @GetMapping("/latest") // GET /api/recommendations/latest
    public ResponseEntity<RecommendationResponse> findLatest() {
        return recommendationService.findLatest()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(summary = "추천 적용 / 넘기기 (status: APPLIED 또는 IGNORED)")
    @PatchMapping("/{id}") // PATCH /api/recommendations/12  본문: { "status": "APPLIED" }
    public RecommendationResponse handle(@PathVariable Long id, @Valid @RequestBody RecommendationStatusRequest request) {
        return recommendationService.handle(id, request.status());
    }
}
