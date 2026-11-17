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

// setting 테이블 (환경 설정 값, key-value)
@Entity
@Table(name = "setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setting {

    @Id
    @Column(name = "setting_key", length = 40)
    private String settingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_name", nullable = false, length = 15)
    private SettingGroup groupName;

    @Column(nullable = false, length = 50)
    private String label;

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
