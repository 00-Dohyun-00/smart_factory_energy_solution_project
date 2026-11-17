package com.smartfactory.energy.ess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ESS(배터리) 1분 상태 (ess_status_minute 테이블).
 * 시뮬레이터가 1분마다 1줄씩 저장합니다.
 */
@Entity
@Table(name = "ess_status_minute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EssStatusMinute {

    // 측정 시각
    @Id
    private LocalDateTime ts;

    // 출력 kW (+ 방전 / - 충전)
    @Column(columnDefinition = "numeric")
    private Double powerKw;

    // 그 1분 동안 충전한 양 kWh
    @Column(columnDefinition = "numeric")
    private Double chargeKwh;

    // 그 1분 동안 방전한 양 kWh
    @Column(columnDefinition = "numeric")
    private Double dischargeKwh;

    // 배터리 잔량 % (SOC)
    @Column(columnDefinition = "numeric")
    private Double soc;

    // 배터리실 온도 ℃
    @Column(columnDefinition = "numeric")
    private Double batteryTemp;

    // 상태: CHARGE(충전) / DISCHARGE(방전) / IDLE(대기)
    @Column(length = 10)
    private String status;
}
