package com.smartfactory.energy.sitepower.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 지금 상태 1건 (모니터링 > 통합, 설비 모니터링). 5초마다 불림.
 */
public record SitePowerLatestResponse(
        LocalDateTime recordedAt,     // 기록 시각 (site_power_minute 최신 1줄의 ts)
        Double loadKw,                // 공장 전체 사용 전력
        Double solarKw,               // 태양광 발전
        Double chargeKw,              // 배터리 충전 전력 (충전 중이 아니면 0)
        Double dischargeKw,           // 배터리 방전 전력 (방전 중이 아니면 0)
        Double gridKw,                // 한전에서 사는 전력
        Double socPercent,            // 배터리 잔량 %
        Double batteryTempC,          // 배터리실 온도
        String pcsStatus,             // STANDBY / CHARGING / DISCHARGING
        Double sohPercent,            // 배터리 건강도 (setting: ess.soh_percent)
        WeatherNow weather,           // 날씨 (기록 없으면 null)
        int simulationSpeed,          // 배속 표시용. 백엔드는 1 고정
        LocalDateTime windowStart,    // 지금 15분 구간 시작
        LocalDateTime windowEnd,      // 지금 15분 구간 끝
        long remainingSeconds,        // 구간 끝까지 남은 초
        Double predictedKw,           // 이번 구간 예상 피크
        Double targetPeakKw,          // 목표 피크
        Double warnKw,                // 주의선 = 목표 × 주의선 비율
        String peakStatus,            // NORMAL / WARNING / CRITICAL
        Double defenseKw,             // 추가 방전 (목표 안으로 들어오려면 지금부터 더 방전해야 하는 kW)
        List<WindowPoint> windowPoints, // 구간 시작부터 쌓인 값
        Double todayPeakKw,           // 오늘 최대 15분 피크 (없으면 null)
        Double monthPeakKw,           // 이번 달 최대 15분 피크 (없으면 null)
        Tariff tariff,                // 요금 시간대
        SourceRatio sourceRatio,      // 공장 전력 중 공급원 비율
        TodayEnergy todayEnergy,      // 오늘 누적 전력량
        List<EquipmentPower> equipments // 설비별 전력
) {

    // 날씨
    public record WeatherNow(String text, Double tempC) {
    }

    // 구간 시작 후 minute분까지 쌓인 값 (그 분까지 한전 구매 합 ÷ 15)
    public record WindowPoint(int minute, Double demandKw) {
    }

    // 요금 시간대. until = 지금 시간대가 끝나는 시각, nextLabel = 다음 시간대 이름
    public record Tariff(String period, String label, String until, String nextLabel, List<TariffBand> periods) {
    }

    // 하루 시간대표 한 칸
    public record TariffBand(String from, String to, String period, String label) {
    }

    // 공급원 비율 %
    public record SourceRatio(Double gridPercent, Double solarPercent, Double essPercent) {
    }

    // 오늘 0시부터 지금까지 누적 kWh
    public record TodayEnergy(Double gridKwh, Double solarKwh, Double dischargeKwh, Double chargeKwh) {
    }

    // 설비 1개의 최신 1분 전력. zoneId = equipment.zone_code (예: Z2), ratedKw = 정격 전력
    public record EquipmentPower(String id, String zoneId, String name, Double ratedKw, Double loadKw,
                                 String origin, String state) {
    }
}
