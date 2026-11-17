package com.smartfactory.energy.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기준값 설정 (setting 테이블). 키-값 한 쌍이 1줄입니다. 예: peak.target_kw = 1800
 */
@Entity
@Table(name = "setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {

    // 설정 이름 (예: peak.target_kw)
    @Id
    @Column(name = "setting_key", length = 40)
    private String settingKey;

    // 묶음: PEAK_TARIFF(요금) / ESS / OPERATION(운영) / SOLAR(태양광) / ENVIRONMENT(환경)
    @Enumerated(EnumType.STRING)
    @Column(name = "group_name", nullable = false, length = 15)
    private SettingGroup groupName;

    // 화면에 보여 줄 이름 (예: 목표 피크)
    @Column(nullable = false, length = 50)
    private String label;

    // 설정값. 숫자도 글자로 저장하고, 쓸 때 숫자로 바꿉니다.
    @Column(nullable = false, length = 50)
    private String value;

    @Column(length = 15)
    private String unit;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false, length = 30)
    private String updatedBy;

    public Setting(String settingKey, SettingGroup groupName, String label, String value, String unit,
                    LocalDateTime updatedAt, String updatedBy) {
        this.settingKey = settingKey;
        this.groupName = groupName;
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
