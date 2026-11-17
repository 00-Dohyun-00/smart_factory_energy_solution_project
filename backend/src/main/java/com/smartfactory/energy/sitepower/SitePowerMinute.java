package com.smartfactory.energy.sitepower;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공장 전체 1분 전력 (site_power_minute 테이블).
 * 시뮬레이터가 1분마다 1줄씩 저장합니다.
 */
@Entity
@Table(name = "site_power_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SitePowerMinute {

    // 측정 시각 (1분에 1줄이라 시각이 곧 기본키)
    @Id
    private LocalDateTime ts;

    // 한전 구매 kW
    @Column(columnDefinition = "numeric")
    private Double gridKw;

    // 태양광 발전 kW
    @Column(columnDefinition = "numeric")
    private Double solarKw;

    // ESS kW (+ 방전 / - 충전)
    @Column(columnDefinition = "numeric")
    private Double essKw;

    // 공장 소비 kW
    @Column(columnDefinition = "numeric")
    private Double loadKw;
}
