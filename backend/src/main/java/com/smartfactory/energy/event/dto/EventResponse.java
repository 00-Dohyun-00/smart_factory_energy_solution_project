package com.smartfactory.energy.event.dto;

import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventStage;
import com.smartfactory.energy.event.EventType;
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
        String category,        // 화면의 종류 칸: STAGE / RECOMMEND / ACTION ... (eventType을 화면 코드로 바꾼 값)
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
                toCategory(eventLog.getEventType()),
                eventLog.getRecommendationId()
        );
    }

    // DB 이벤트 종류를 화면의 종류 코드로 바꿈 (프론트 목데이터의 category와 같은 값)
    // 화면에 없는 종류(통신 COMM)는 이름 그대로 보냄
    private static String toCategory(EventType type) {
        return switch (type) {
            case STAGE_CHANGE -> "STAGE";
            case RECOMMEND -> "RECOMMEND";
            case HANDLE -> "ACTION";
            case INTERVAL_END -> "WINDOW";
            case SETTING -> "SETTING";
            case COMM -> "COMM";
        };
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
