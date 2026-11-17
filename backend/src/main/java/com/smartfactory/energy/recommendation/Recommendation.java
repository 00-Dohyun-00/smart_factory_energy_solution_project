package com.smartfactory.energy.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    // 추천 번호 (1, 2, 3... 자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recommendationId;

    // 추천이 나온 시각
    @Column(nullable = false)
    private LocalDateTime recommendedAt;

    // 대상 15분 구간의 시작 시각 (예: 14:00 → 14:00~14:15 구간)
    @Column(nullable = false)
    private LocalDateTime intervalStart;

    // 추천 행동: CHARGE(충전) / DISCHARGE(방전) / IDLE(대기)
    @Column(nullable = false, length = 10)
    private String action;

    // 추천 출력 kW
    @Column(columnDefinition = "numeric")
    private Double powerKw;

    // 추천한 이유 종류 (예: PEAK_DEFENSE = 피크 방어)
    @Column(length = 25)
    private String reasonType;

    // 추천 설명 문장
    @Column(columnDefinition = "text")
    private String description;

    // 추천할 때 예상한 15분 평균 kW
    @Column(columnDefinition = "numeric")
    private Double predictedAvgKw;

    // 목표 피크 kW
    @Column(columnDefinition = "numeric")
    private Double targetPeakKw;

    // 처리 상태: RECOMMENDED(추천중) / APPLIED(적용) / IGNORED(무시) / AUTO(자동)
    @Column(nullable = false, length = 10)
    private String status;

    // 처리한 사람: "운영자" 또는 "자동 모드". 처리 전에는 null
    @Column(length = 30)
    private String handledBy;

    // 처리 시각. 처리 전에는 null
    private LocalDateTime handledAt;
}
