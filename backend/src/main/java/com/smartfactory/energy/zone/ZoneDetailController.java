package com.smartfactory.energy.zone;

import com.smartfactory.energy.zone.dto.ZoneDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 구역 목록(ZoneController)과 주소는 같지만, 설비 모니터링 화면용이라 따로 둠
@Tag(name = "구역", description = "구역(Zone) 조회 API")
@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneDetailController {

    private final ZoneDetailService zoneDetailService;

    @Operation(summary = "구역 상세 (최근 1시간 1분 간격 전력, 오늘 최대·평균·사용량)")
    @GetMapping("/{zoneCode}") // GET /api/zones/Z2
    public ZoneDetailResponse getDetail(@PathVariable String zoneCode) {
        return zoneDetailService.getDetail(zoneCode);
    }
}
