package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공장 전체 1분 전력 (site_power_minute 테이블).
 * 시뮬레이터가 1분마다 1줄씩 저장하고, 화면/판단 엔진이 조회하는 실시간 테이블입니다.
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
    @Column(name = "grid_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal gridKw;

    // 태양광 발전 kW
    @Column(name = "solar_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal solarKw;

    // ESS kW (+ 방전 / - 충전)
    @Column(name = "ess_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal essKw;

    // 공장 소비 kW
    @Column(name = "load_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadKw;

    public SitePowerMinute(LocalDateTime ts, BigDecimal gridKw, BigDecimal solarKw, BigDecimal essKw,
                            BigDecimal loadKw) {
        this.ts = ts;
        this.gridKw = gridKw;
        this.solarKw = solarKw;
        this.essKw = essKw;
        this.loadKw = loadKw;
    }
}
