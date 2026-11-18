package com.smartfactory.energy.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ESS 추천 이력 (recommendation 테이블).
 * 판단 엔진이 "지금 충전/방전/대기하세요"라고 추천할 때마다 1줄씩 쌓입니다.
 */
@Entity
@Table(name = "recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    // 추천이 나온 시각
    @Column(name = "recommended_at", nullable = false)
    private LocalDateTime recommendedAt;

    // 대상 15분 구간의 시작 시각 (예: 14:00 → 14:00~14:15 구간)
    @Column(name = "interval_start", nullable = false)
    private LocalDateTime intervalStart;

    // 추천 행동: CHARGE(충전) / DISCHARGE(방전) / IDLE(대기)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecommendationAction action;

    // 추천 출력 kW
    @Column(name = "power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal powerKw;

    // 추천한 이유 종류 (예: PEAK_DEFENSE = 피크 방어)
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 25)
    private RecommendationReasonType reasonType;

    // 추천 설명 문장
    @Column(columnDefinition = "text")
    private String description;

    // 추천할 때 예상한 15분 평균 kW
    @Column(name = "predicted_avg_kw", precision = 10, scale = 2)
    private BigDecimal predictedAvgKw;

    // 목표 피크 kW
    @Column(name = "target_peak_kw", precision = 10, scale = 2)
    private BigDecimal targetPeakKw;

    // 처리 상태: PENDING(대기중) / APPLIED(적용) / AUTO(자동) / SKIPPED(넘김) / EXPIRED(만료)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecommendationStatus status;

    // 처리한 사람: "운영자" 또는 "자동 모드". 처리 전에는 null
    @Column(name = "handled_by", length = 30)
    private String handledBy;

    // 처리 시각. 처리 전에는 null
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public Recommendation(LocalDateTime recommendedAt, LocalDateTime intervalStart, RecommendationAction action,
                           BigDecimal powerKw, RecommendationReasonType reasonType, String description,
                           BigDecimal predictedAvgKw, BigDecimal targetPeakKw, RecommendationStatus status,
                           String handledBy, LocalDateTime handledAt) {
        this.recommendedAt = recommendedAt;
        this.intervalStart = intervalStart;
        this.action = action;
        this.powerKw = powerKw;
        this.reasonType = reasonType;
        this.description = description;
        this.predictedAvgKw = predictedAvgKw;
        this.targetPeakKw = targetPeakKw;
        this.status = status;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
    }

    // 추천 처리 (운영자가 적용 / 넘기기 했을 때)
    public void handle(RecommendationStatus status, String handledBy, LocalDateTime handledAt) {
        this.status = status;
        this.handledBy = handledBy;
        this.handledAt = handledAt;
    }
}
