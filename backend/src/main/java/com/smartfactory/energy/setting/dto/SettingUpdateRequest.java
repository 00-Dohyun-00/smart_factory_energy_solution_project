package com.smartfactory.energy.setting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 기준값 저장 요청 1개. 예: { "key": "peak.target_kw", "value": "1750" }
 */
public record SettingUpdateRequest(
        @NotBlank String key,
        @NotBlank String value
) {
}
