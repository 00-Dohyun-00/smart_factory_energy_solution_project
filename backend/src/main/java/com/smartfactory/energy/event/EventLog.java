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

// event_log 테이블 (운영 로그: 단계 변경, 추천, 처리, 통신, 설정 변경 등)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 15)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private EventStage stage;

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
