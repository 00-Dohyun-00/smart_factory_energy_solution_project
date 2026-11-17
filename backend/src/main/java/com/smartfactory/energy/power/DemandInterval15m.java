package com.smartfactory.energy.power;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

// demand_interval_15m 뷰(view)를 읽기 전용으로 매핑합니다. (15분 단위 수요 예측/절감 집계)
@Entity
@Immutable
@Subselect("SELECT interval_start, actual_avg_kw, no_ess_avg_kw, reduced_kw, minute_count FROM demand_interval_15m")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemandInterval15m {

    @Id
    @Column(name = "interval_start")
    private LocalDateTime intervalStart;

    @Column(name = "actual_avg_kw")
    private BigDecimal actualAvgKw;

    @Column(name = "no_ess_avg_kw")
    private BigDecimal noEssAvgKw;

    @Column(name = "reduced_kw")
    private BigDecimal reducedKw;

    @Column(name = "minute_count")
    private Long minuteCount;
}
