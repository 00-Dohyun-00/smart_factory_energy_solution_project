package com.smartfactory.energy.setting;

import com.smartfactory.energy.setting.dto.SettingResponse;
import com.smartfactory.energy.setting.dto.SettingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "환경 설정", description = "기준값 조회·저장 API")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @Operation(summary = "기준값 전체 조회")
    @GetMapping // GET /api/settings
    public List<SettingResponse> findAll() {
        return settingService.findAll();
    }

    // 본문이 배열이라 @Valid 검사 오류가 공통 처리기에서 400으로 바뀌지 않음 → 빈 값 검사는 Service에서 함
    @Operation(summary = "기준값 저장 (여러 개 한 번에, 바뀐 값만 저장하고 변경 기록 남김)")
    @PutMapping // PUT /api/settings  본문: [{ "key": "peak.target_kw", "value": "1750" }]
    public List<SettingResponse> update(@RequestBody List<SettingUpdateRequest> requests) {
        return settingService.update(requests);
    }
}
