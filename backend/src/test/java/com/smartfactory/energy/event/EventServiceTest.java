package com.smartfactory.energy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartfactory.energy.event.dto.EventResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

// 최근 이벤트: limit 범위, DB 단계 → 화면 등급 바꾸기
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventLogRepository eventLogRepository;
    @InjectMocks EventService eventService;

    @Test
    void limit은_1부터_50까지() {
        assertThatThrownBy(() -> eventService.findRecent(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventService.findRecent(51)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 단계는_화면_등급으로_바꿈_주의는_WARNING_경고는_CRITICAL_없으면_INFO() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 10, 14, 0);
        when(eventLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new EventLog(at, EventType.STAGE_CHANGE, EventStage.WARNING, "경고", null),
                new EventLog(at, EventType.STAGE_CHANGE, EventStage.CAUTION, "주의", null),
                new EventLog(at, EventType.STAGE_CHANGE, EventStage.NORMAL, "정상", null),
                new EventLog(at, EventType.HANDLE, null, "방전 500 kW 적용 (운영자)", 12L)
        )));

        List<EventResponse> events = eventService.findRecent(6);

        assertThat(events).extracting(EventResponse::level).containsExactly("CRITICAL", "WARNING", "NORMAL", "INFO");
        assertThat(events.get(3).eventType()).isEqualTo("HANDLE");
        // 화면의 종류 칸 코드: 단계 변화 STAGE, 적용·넘김 ACTION
        assertThat(events).extracting(EventResponse::category).containsExactly("STAGE", "STAGE", "STAGE", "ACTION");
        assertThat(events.get(3).recommendationId()).isEqualTo(12L);
    }
}
