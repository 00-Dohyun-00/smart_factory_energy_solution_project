package com.smartfactory.energy.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 추천 처리 요청. 예: { "status": "APPLIED" } 또는 { "status": "IGNORED" }
 */
public record RecommendationStatusRequest(
        @NotBlank String status
) {
}
