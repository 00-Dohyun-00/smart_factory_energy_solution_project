package com.smartfactory.energy.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // 이벤트 종류 (예: STAGE_CHANGE 단계 변화, RECOMMEND 추천, COMM 통신)
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 15)
    private EventType eventType;

    // 단계: NORMAL(정상) / CAUTION(주의) / WARNING(경고)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private EventStage stage;

    // 화면에 그대로 보여 줄 문장
    @Column(nullable = false, columnDefinition = "text")
    private String message;

    // recommendation과 관계로 묶지 않고 recommendationId 값만 둡니다. (단순하게 유지)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    public EventLog(LocalDateTime occurredAt, EventType eventType, EventStage stage, String message,
                     Long recommendationId) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.stage = stage;
        this.message = message;
        this.recommendationId = recommendationId;
    }
}
