package com.smartfactory.energy.event;

import com.smartfactory.energy.event.dto.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
}
