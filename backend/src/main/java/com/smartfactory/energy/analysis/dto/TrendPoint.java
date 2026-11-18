package com.smartfactory.energy.analysis.dto;

import java.time.LocalDateTime;

/**
 * 추이 그래프의 점 1개. interval(15분/1시간/1일) 단위로 묶은 평균.
 * 그 구간에 기록이 없는 값은 null.
 */
public record TrendPoint(
        LocalDateTime recordedAt, // 구간 시작 시각
        Double loadKw,            // 공장 소비
        Double gridKw,            // 한전 구매
        Double solarKw,           // 태양광
        Double essKw,             // ESS (+ 방전 / - 충전)
        Double socPercent,        // 배터리 잔량 %
        Double roomTempC,         // 배터리실 온도
        Double outsideTempC       // 바깥 기온
) {
}
