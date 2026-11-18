package com.smartfactory.energy.event.dto;

import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventStage;
import java.time.LocalDateTime;

/**
 * 이벤트 1건 응답.
 */
public record EventResponse(
        Long id,
        LocalDateTime occurredAt,
        String level,           // CRITICAL / WARNING / NORMAL / INFO (화면 코드)
        String message,         // 화면에 그대로 보여 줄 문장
        String eventType,       // STAGE_CHANGE, RECOMMEND, HANDLE ...
        Long recommendationId   // 관련 추천 번호. 없으면 null
) {

    // Entity -> DTO 변환
    public static EventResponse from(EventLog eventLog) {
        return new EventResponse(
                eventLog.getEventId(),
                eventLog.getOccurredAt(),
                toLevel(eventLog.getStage()),
                eventLog.getMessage(),
                eventLog.getEventType().name(),
                eventLog.getRecommendationId()
        );
    }

    // DB 단계를 화면 코드로 바꿈 (CAUTION 주의 → WARNING, WARNING 경고 → CRITICAL, 단계 없음 → INFO)
    private static String toLevel(EventStage stage) {
        if (stage == null) {
            return "INFO";
        }
        return switch (stage) {
            case NORMAL -> "NORMAL";
            case CAUTION -> "WARNING";
            case WARNING -> "CRITICAL";
        };
    }
}
