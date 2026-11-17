package com.smartfactory.energy.zone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// zone 테이블 (구역 기준 데이터)
@Entity
@Table(name = "zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "zone_code", nullable = false, length = 10)
    private String zoneCode;

    public Zone(String name, Integer displayOrder, String zoneCode) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.zoneCode = zoneCode;
    }
}
