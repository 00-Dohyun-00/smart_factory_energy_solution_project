package com.smartfactory.energy.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// setting_history 테이블 (설정 변경 이력)
@Entity
@Table(name = "setting_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    // setting과 관계로 묶지 않고 settingKey 값만 둡니다. (단순하게 유지)
    @Column(name = "setting_key", nullable = false, length = 40)
    private String settingKey;

    @Column(name = "old_value", length = 50)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 50)
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 30)
    private String changedBy;

    public SettingHistory(LocalDateTime changedAt, String settingKey, String oldValue, String newValue,
                           String changedBy) {
        this.changedAt = changedAt;
        this.settingKey = settingKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
    }
}
