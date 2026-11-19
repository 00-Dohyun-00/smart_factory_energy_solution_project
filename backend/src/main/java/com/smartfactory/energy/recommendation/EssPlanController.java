package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.recommendation.dto.EssPlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ESS 계획", description = "판단 엔진이 짠 24시간 ESS 계획 API")
@RestController
@RequestMapping("/api/ess-plans")
@RequiredArgsConstructor
public class EssPlanController {

    private final RecommendationEngine recommendationEngine;

    // 계획은 판단 엔진을 돌리는 서버(시뮬레이터를 켠 서버) 메모리에만 있음. 다른 서버에서는 404
    @Operation(summary = "가장 최근 24시간 ESS 계획 (15분 96칸: 예상 부하·태양광, 충·방전, 예상 잔량)")
    @GetMapping("/latest") // GET /api/ess-plans/latest
    public EssPlanResponse findLatest() {
        return recommendationEngine.latestPlan()
                .orElseThrow(() -> new NotFoundException("아직 ESS 계획이 없습니다. 판단 엔진은 시뮬레이터를 켠 서버에서만 돕니다."));
    }
}
