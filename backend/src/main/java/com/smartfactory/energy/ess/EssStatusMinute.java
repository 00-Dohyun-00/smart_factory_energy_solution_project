package com.smartfactory.energy.ess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// ess_status_minute 테이블 (ESS 1분 단위 상태. 실시간 테이블)
@Entity
@Table(name = "ess_status_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EssStatusMinute {

    @Id
    private LocalDateTime ts;

    @Column(name = "power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal powerKw;

    @Column(name = "charge_kwh", nullable = false, precision = 10, scale = 4)
    private BigDecimal chargeKwh;

    @Column(name = "discharge_kwh", nullable = false, precision = 10, scale = 4)
    private BigDecimal dischargeKwh;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal soc;

    @Column(name = "battery_temp", precision = 5, scale = 2)
    private BigDecimal batteryTemp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EssStatus status;

    public EssStatusMinute(LocalDateTime ts, BigDecimal powerKw, BigDecimal chargeKwh, BigDecimal dischargeKwh,
                            BigDecimal soc, BigDecimal batteryTemp, EssStatus status) {
        this.ts = ts;
        this.powerKw = powerKw;
        this.chargeKwh = chargeKwh;
        this.dischargeKwh = dischargeKwh;
        this.soc = soc;
        this.batteryTemp = batteryTemp;
        this.status = status;
    }
}
