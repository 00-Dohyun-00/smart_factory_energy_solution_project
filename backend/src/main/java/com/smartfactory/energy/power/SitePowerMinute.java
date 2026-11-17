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

// site_power_minute 테이블 (공장 전체 1분 집계. 화면/판단 엔진이 조회하는 실시간 테이블)
@Entity
@Table(name = "site_power_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SitePowerMinute {

    @Id
    private LocalDateTime ts;

    @Column(name = "grid_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal gridKw;

    @Column(name = "solar_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal solarKw;

    @Column(name = "ess_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal essKw;

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
