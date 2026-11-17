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

// recommendation 테이블 (판단 엔진의 충전/방전 추천 결과)
@Entity
@Table(name = "recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @Column(name = "recommended_at", nullable = false)
    private LocalDateTime recommendedAt;

    @Column(name = "interval_start", nullable = false)
    private LocalDateTime intervalStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecommendationAction action;

    @Column(name = "power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal powerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 25)
    private RecommendationReasonType reasonType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "predicted_avg_kw", precision = 10, scale = 2)
    private BigDecimal predictedAvgKw;

    @Column(name = "target_peak_kw", precision = 10, scale = 2)
    private BigDecimal targetPeakKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecommendationStatus status;

    @Column(name = "handled_by", length = 30)
    private String handledBy;

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
}
