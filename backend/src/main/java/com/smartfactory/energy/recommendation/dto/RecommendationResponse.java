package com.smartfactory.energy.recommendation.dto;

import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 추천 1건 응답. 필드 이름은 프론트 목데이터(fakeBackend.js)와 같게 맞춤.
 */
public record RecommendationResponse(
        Long id,
        LocalDateTime decidedAt,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        RecommendationReasonType reasonType,
        String ruleLabel,
        RecommendationAction action,
        BigDecimal powerKw,
        Integer durationMinutes,
        BigDecimal predictedKw,
        BigDecimal targetPeakKw,
        String reason,
        List<Object> usedValues,
        String status,
        String appliedBy,
        LocalDateTime appliedAt,
        BigDecimal resultPeakKw,
        String resultStatus
) {

    // Entity -> DTO 변환. resultPeakKw는 그 15분 구간이 끝난 뒤의 실제 피크 (아직 안 끝났으면 null)
    public static RecommendationResponse from(Recommendation recommendation, BigDecimal resultPeakKw) {
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
                toStatusCode(recommendation.getStatus()),
                recommendation.getHandledBy(),
                recommendation.getHandledAt(),
                resultPeakKw,
                toResultStatus(resultPeakKw, recommendation.getTargetPeakKw())
        );
    }

    // 처리 상태를 화면 코드로 바꿈 (SKIPPED·EXPIRED → IGNORED, PENDING → RECOMMENDED)
    private static String toStatusCode(RecommendationStatus status) {
        return switch (status) {
            case APPLIED -> "APPLIED";
            case AUTO -> "AUTO";
            case SKIPPED, EXPIRED -> "IGNORED";
            case PENDING -> "RECOMMENDED";
        };
    }

    // 실제 피크가 목표 이하면 DEFENDED(달성), 넘으면 EXCEEDED(초과). 아직 모르면 null
    private static String toResultStatus(BigDecimal resultPeakKw, BigDecimal targetPeakKw) {
        if (resultPeakKw == null || targetPeakKw == null) {
            return null;
        }
        return resultPeakKw.compareTo(targetPeakKw) <= 0 ? "DEFENDED" : "EXCEEDED";
    }

    // 이유 코드를 화면에 보여줄 한국어 이름으로 바꿈
    private static String toRuleLabel(RecommendationReasonType reasonType) {
        if (reasonType == null) {
            return "조치 없음";
        }
        return switch (reasonType) {
            case SAFETY_LIMIT -> "안전 제한";
            case PEAK_DEFENSE -> "피크 방어";
            case SOLAR_SURPLUS_CHARGE -> "잉여 충전";
            case OFF_PEAK_CHARGE -> "저요금 충전";
            case ON_PEAK_DISCHARGE -> "고요금 방전";
            case IDLE -> "조치 없음";
        };
    }
}
