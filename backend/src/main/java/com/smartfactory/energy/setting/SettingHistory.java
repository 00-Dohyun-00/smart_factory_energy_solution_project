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

/**
 * 설정 변경 이력 (setting_history 테이블). 설정을 바꿀 때마다 1줄씩 쌓입니다.
 */
@Entity
@Table(name = "setting_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingHistory {

    // 이력 번호 (자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    // 바꾼 시각
    @Column(nullable = false)
    private LocalDateTime changedAt;

    // 바꾼 설정 이름 (setting.setting_key)
    @Column(nullable = false, length = 40)
    private String settingKey;

    // 바꾸기 전 값
    @Column(length = 50)
    private String oldValue;

    // 바꾼 뒤 값
    @Column(length = 50)
    private String newValue;

    // 바꾼 사람
    @Column(length = 30)
    private String changedBy;
}
