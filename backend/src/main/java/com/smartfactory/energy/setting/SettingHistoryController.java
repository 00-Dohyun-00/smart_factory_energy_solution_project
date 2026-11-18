package com.smartfactory.energy.setting;

import com.smartfactory.energy.setting.dto.SettingHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "환경 설정", description = "기준값 조회·저장 API")
@RestController
@RequestMapping("/api/setting-histories")
@RequiredArgsConstructor
public class SettingHistoryController {

    private final SettingService settingService;

    @Operation(summary = "기준값 변경 기록 조회 (최신 순)")
    @GetMapping // GET /api/setting-histories?from=2026-11-01&to=2026-11-18
    public List<SettingHistoryResponse> findHistories(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return settingService.findHistories(from, to);
    }
}
