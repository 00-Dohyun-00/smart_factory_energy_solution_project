package com.smartfactory.energy.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설비 (equipment 테이블). 생산 설비 + 태양광・ESS・한전 계량점까지 모두 여기에 등록합니다.
 */
@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @Column(name = "equipment_id", length = 30)
    private String equipmentId;

    // zone과 관계(ManyToOne)로 묶지 않고 zoneCode 값만 둡니다. (단순하게 유지)
    // GRID-01/SOLAR-01/ESS-01은 구역이 없어서 NULL
    @Column(name = "zone_code", length = 10)
    private String zoneCode;

    @Column(nullable = false, length = 50)
    private String name;

    // 역할: GRID(한전) / SOLAR(태양광) / ESS / LOAD(생산 설비)
    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_role", nullable = false, length = 10)
    private EquipmentRole equipmentRole;

    @Column(name = "rated_kw", precision = 10, scale = 2)
    private BigDecimal ratedKw;

    public Equipment(String equipmentId, String zoneCode, String name, EquipmentRole equipmentRole,
                      BigDecimal ratedKw) {
        this.equipmentId = equipmentId;
        this.zoneCode = zoneCode;
        this.name = name;
        this.equipmentRole = equipmentRole;
        this.ratedKw = ratedKw;
    }
}
