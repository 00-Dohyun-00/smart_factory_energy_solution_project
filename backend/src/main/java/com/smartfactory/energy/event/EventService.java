package com.smartfactory.energy.event;

import com.smartfactory.energy.event.dto.EventResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    // 한 번에 받을 수 있는 최대 개수
    private static final int MAX_LIMIT = 50;

    // 기간 조회로 한 번에 볼 수 있는 최대 시간 (전력 이력 화면은 하루 단위로 봄)
    private static final int MAX_RANGE_HOURS = 48;

    private final EventLogRepository eventLogRepository;

    // 최근 이벤트 limit개 (최신 순)
    public List<EventResponse> findRecent(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1 ~ " + MAX_LIMIT + " 사이여야 합니다.");
        }
        // 발생 시각 최신 순, 같은 시각이면 번호 큰 순
        Sort sort = Sort.by(Sort.Direction.DESC, "occurredAt", "eventId");
        return eventLogRepository.findAll(PageRequest.of(0, limit, sort)).stream()
                .map(EventResponse::from)
                .toList();
    }

    // 기간 안의 이벤트 (오래된 순). from 이상 ~ to 미만
    public List<EventResponse> findInPeriod(LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("시작 시각이 끝 시각보다 빨라야 합니다.");
        }
        if (ChronoUnit.HOURS.between(from, to) > MAX_RANGE_HOURS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_RANGE_HOURS + "시간까지 조회할 수 있습니다.");
        }
        return eventLogRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscEventIdAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }
}
