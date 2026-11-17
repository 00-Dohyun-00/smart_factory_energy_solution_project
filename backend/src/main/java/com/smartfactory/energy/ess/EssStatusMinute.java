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

/**
 * ESS(배터리) 1분 상태 (ess_status_minute 테이블). 시뮬레이터가 1분마다 1줄씩 저장합니다.
 */
@Entity
@Table(name = "ess_status_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EssStatusMinute {

    @Id
    private LocalDateTime ts;

    // 출력 kW (+ 방전 / - 충전)
    @Column(name = "power_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal powerKw;

    // 그 1분 동안 충전한 양 kWh
    @Column(name = "charge_kwh", nullable = false, precision = 10, scale = 4)
    private BigDecimal chargeKwh;

    // 그 1분 동안 방전한 양 kWh
    @Column(name = "discharge_kwh", nullable = false, precision = 10, scale = 4)
    private BigDecimal dischargeKwh;

    // 배터리 잔량 % (SOC)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal soc;

    // 배터리실 온도 ℃
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
