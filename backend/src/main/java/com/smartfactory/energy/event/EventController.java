package com.smartfactory.energy.event;

import com.smartfactory.energy.event.dto.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이벤트", description = "최근 이벤트 API")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "최근 이벤트 목록 (최신 순, limit 기본 6)")
    @GetMapping // GET /api/events?limit=6
    public List<EventResponse> findRecent(@RequestParam(defaultValue = "6") int limit) {
        return eventService.findRecent(limit);
    }

    @Operation(summary = "기간 안의 이벤트 목록 (오래된 순, 최대 48시간)")
    @GetMapping(params = {"from", "to"}) // GET /api/events?from=2026-11-19T00:00:00&to=2026-11-20T00:00:00
    public List<EventResponse> findInPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return eventService.findInPeriod(from, to);
    }
}
