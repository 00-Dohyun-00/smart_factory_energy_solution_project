package com.smartfactory.energy.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    @Column(length = 40)
    private String settingKey;

    // 묶음: 피크 / ESS / 운영 / 태양광 / 환경
    @Column(length = 15)
    private String groupName;

    // 화면에 보여 줄 이름 (예: 목표 피크)
    @Column(length = 50)
    private String label;

    // 설정값. 숫자도 글자로 저장하고, 쓸 때 숫자로 바꿉니다.
    @Column(nullable = false, length = 50)
    private String value;

    // 단위 (예: kW, %)
    @Column(length = 15)
    private String unit;

    // 마지막으로 바꾼 시각
    private LocalDateTime updatedAt;

    // 마지막으로 바꾼 사람 (예: 운영자)
    @Column(length = 30)
    private String updatedBy;
}
