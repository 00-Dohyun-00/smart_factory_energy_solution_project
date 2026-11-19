package com.smartfactory.energy.event;

import com.smartfactory.energy.event.dto.EventResponse;
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
}
