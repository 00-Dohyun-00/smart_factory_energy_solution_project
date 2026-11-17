package com.smartfactory.energy.event;

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
 * 시스템 이벤트 기록 (event_log 테이블).
 * 피크 단계 변화, 추천 적용/넘김, 통신 끊김 같은 일이 생길 때 1줄씩 쌓입니다.
 */
@Entity
@Table(name = "event_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLog {

    // 이벤트 번호 (자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    // 발생 시각
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // 이벤트 종류 (예: STAGE_CHANGE 단계 변화, RECOMMEND 추천, COMM 통신)
    @Column(nullable = false, length = 15)
    private String eventType;

    // 단계: NORMAL(정상) / WARNING(주의) / CRITICAL(경고)
    @Column(length = 10)
    private String stage;

    // 화면에 그대로 보여 줄 문장
    @Column(nullable = false, columnDefinition = "text")
    private String message;

    // 관련 추천 번호 (recommendation.recommendation_id). 없으면 null
    private Long recommendationId;
}
