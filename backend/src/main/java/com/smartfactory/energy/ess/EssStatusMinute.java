package com.smartfactory.energy.ess;

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
 * ESS(배터리) 1분 상태 (ess_status_minute 테이블). 시뮬레이터가 1분마다 1줄씩 저장합니다.
 * 충·방전량·출력·상태는 site_power_minute.ess_kw로 그때그때 계산하므로 여기 저장하지 않습니다.
 */
@Entity
@Table(name = "ess_status_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EssStatusMinute {

    @Id
    private LocalDateTime ts;

    // 배터리 잔량 % (SOC)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal soc;

    // 배터리실 온도 ℃
    @Column(name = "battery_temp", precision = 5, scale = 2)
    private BigDecimal batteryTemp;

    public EssStatusMinute(LocalDateTime ts, BigDecimal soc, BigDecimal batteryTemp) {
        this.ts = ts;
        this.soc = soc;
        this.batteryTemp = batteryTemp;
    }
}
