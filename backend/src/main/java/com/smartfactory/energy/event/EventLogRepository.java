package com.smartfactory.energy.event;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    // 그 종류의 가장 최근 이벤트 1건 (예: 마지막 단계 변화)
    Optional<EventLog> findFirstByEventTypeOrderByOccurredAtDescEventIdDesc(EventType eventType);

    // 그 종류·시각의 이벤트가 이미 있는지 (같은 구간 종료를 두 번 남기지 않으려고)
    boolean existsByEventTypeAndOccurredAt(EventType eventType, LocalDateTime occurredAt);

    // 기간 안의 이벤트 (오래된 순). 전력 이력 화면용
    List<EventLog> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(
            LocalDateTime start, LocalDateTime end);
}
