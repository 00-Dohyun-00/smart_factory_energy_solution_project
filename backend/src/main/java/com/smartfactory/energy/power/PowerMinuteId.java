package com.smartfactory.energy.power;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * power_minute 테이블의 기본키 (설비 ID + 시각, 두 칸을 합쳐서 기본키).
 * 기본키가 칸 2개일 때 JPA는 이렇게 따로 클래스를 하나 만들어야 합니다.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PowerMinuteId implements Serializable {

    private String equipmentId;
    private LocalDateTime ts;
}
