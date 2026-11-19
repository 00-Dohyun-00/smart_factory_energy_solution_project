package com.smartfactory.energy.sitepower;

import com.smartfactory.energy.sitepower.dto.SitePowerLatestResponse;
import com.smartfactory.energy.sitepower.dto.SitePowerQuarterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공장 전력", description = "모니터링 화면용 전력·배터리 상태 API")
@RestController
@RequestMapping("/api/site-powers")
@RequiredArgsConstructor
public class SitePowerController {

    private final SitePowerService sitePowerService;

    @Operation(summary = "지금 상태 1건 (전력·배터리·15분 피크·요금 시간대·설비별 전력)")
    @GetMapping("/latest") // GET /api/site-powers/latest
    public SitePowerLatestResponse getLatest() {
        return sitePowerService.getLatest();
    }

    @Operation(summary = "오늘 0시부터 지금까지 15분 간격 목록")
    @GetMapping("/today") // GET /api/site-powers/today
    public List<SitePowerQuarterResponse> getToday() {
        return sitePowerService.getToday();
    }
}
