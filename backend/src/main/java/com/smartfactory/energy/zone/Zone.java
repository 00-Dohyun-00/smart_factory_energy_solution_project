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

/**
 * 공장 구역 (zone 테이블). 예: Z1 열교환기 가공, Z2 브레이징 ...
 */
@Entity
@Table(name = "zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    // 구역 번호 (자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long zoneId;

    // 구역 이름 (예: 브레이징)
    @Column(nullable = false, unique = true, length = 30)
    private String name;

    // 화면에 보여 줄 순서
    private Integer displayOrder;

    // 구역 코드 (예: Z2). 화면과 API 주소에서 쓰는 값
    @Column(nullable = false, unique = true, length = 10)
    private String zoneCode;
}
