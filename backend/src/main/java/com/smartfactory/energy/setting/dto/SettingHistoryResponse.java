package com.smartfactory.energy.setting.dto;

import com.smartfactory.energy.setting.SettingHistory;
import java.time.LocalDateTime;

/**
 * 기준값 변경 기록 1건 응답.
 */
public record SettingHistoryResponse(
        Long id,
        LocalDateTime changedAt,
        String key,
        String label,      // setting.label (키로 찾아 붙임)
        String oldValue,
        String newValue,
        String changedBy
) {

    // Entity -> DTO 변환. label은 setting 테이블에서 찾아서 넘겨받음
    public static SettingHistoryResponse from(SettingHistory history, String label) {
        return new SettingHistoryResponse(
                history.getHistoryId(),
                history.getChangedAt(),
                history.getSettingKey(),
                label,
                history.getOldValue(),
                history.getNewValue(),
                history.getChangedBy()
        );
    }
}
