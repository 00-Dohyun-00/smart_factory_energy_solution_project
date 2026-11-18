package com.smartfactory.energy.setting.dto;

import com.smartfactory.energy.setting.Setting;
import com.smartfactory.energy.setting.SettingGroup;
import java.time.LocalDateTime;

/**
 * 기준값 1개 응답. value는 DB가 글자라서 글자 그대로 보냄 (숫자 변환은 프론트에서).
 */
public record SettingResponse(
        String key,
        String group,
        String label,
        String value,
        String unit,
        LocalDateTime updatedAt,
        String updatedBy
) {

    // Entity -> DTO 변환
    public static SettingResponse from(Setting setting) {
        return new SettingResponse(
                setting.getSettingKey(),
                toGroupLabel(setting.getGroupName()),
                setting.getLabel(),
                setting.getValue(),
                setting.getUnit(),
                setting.getUpdatedAt(),
                setting.getUpdatedBy()
        );
    }

    // 묶음 코드를 화면에 보여줄 이름으로 바꿈
    private static String toGroupLabel(SettingGroup group) {
        return switch (group) {
            case PEAK_TARIFF -> "피크";
            case ESS -> "ESS";
            case OPERATION -> "운영";
            case SOLAR -> "태양광";
            case ENVIRONMENT -> "환경";
        };
    }
}
