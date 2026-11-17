package com.smartfactory.energy.recommendation.dto;

import com.smartfactory.energy.recommendation.Recommendation;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 추천 1건 응답. 필드 이름은 프론트 목데이터(fakeBackend.js)와 같게 맞췄습니다.
 */
public record RecommendationResponse(
        Long id,
        LocalDateTime decidedAt,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        String reasonType,
        String ruleLabel,
        String action,
        Double powerKw,
        Integer durationMinutes,
        Double predictedKw,
        Double targetPeakKw,
        String reason,
        List<Object> usedValues,
        String status,
        String appliedBy,
        LocalDateTime appliedAt,
        Double resultPeakKw,
        String resultStatus
) {

    // Entity → DTO 변환
    public static RecommendationResponse from(Recommendation recommendation) {
        return new RecommendationResponse(
                recommendation.getRecommendationId(),
                recommendation.getRecommendedAt(),
                recommendation.getIntervalStart(),
                recommendation.getIntervalStart().plusMinutes(15), // 구간 끝 = 시작 + 15분
                recommendation.getReasonType(),
                toRuleLabel(recommendation.getReasonType()),
                recommendation.getAction(),
                recommendation.getPowerKw(),
                null, // 지속 시간: ERD에 칸이 없어서 아직 null
                recommendation.getPredictedAvgKw(),
                recommendation.getTargetPeakKw(),
                recommendation.getDescription(),
                List.of(), // 판단에 쓴 값: ERD에 칸이 없어서 아직 빈 목록
                recommendation.getStatus(),
                recommendation.getHandledBy(),
                recommendation.getHandledAt(),
                null, // 결과 피크: demand_interval_15m 뷰가 생기면 채움
                null // 결과(막음/뚫림): 결과 피크가 생기면 계산
        );
    }

    // 이유 코드를 화면에 보여줄 한국어 이름으로 바꿈
    private static String toRuleLabel(String reasonType) {
        if (reasonType == null) {
            return "조치 없음";
        }
        return switch (reasonType) {
            case "SAFETY_LIMIT" -> "안전 제한";
            case "PEAK_DEFENSE" -> "피크 방어";
            case "SURPLUS_CHARGE" -> "잉여 충전";
            case "LOW_PRICE_CHARGE" -> "저요금 충전";
            case "HIGH_PRICE_DISCHARGE" -> "고요금 방전";
            case "NONE" -> "조치 없음";
            default -> reasonType; // 모르는 코드는 그대로 보여 줌
        };
    }
}
