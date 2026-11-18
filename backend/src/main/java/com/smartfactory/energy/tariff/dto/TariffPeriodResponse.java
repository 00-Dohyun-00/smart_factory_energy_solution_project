package com.smartfactory.energy.tariff.dto;

import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodType;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 요금 시간대 1줄 응답. 예: WINTER / PEAK / 최대부하 / 10:00 ~ 12:00 / 단가
 */
public record TariffPeriodResponse(
        Long id,
        String season,      // SUMMER / SPRING_FALL / WINTER
        String period,      // LIGHT / MID / PEAK (화면 코드)
        String label,       // 경부하 / 중간부하 / 최대부하
        String from,        // 시작 (HH:mm)
        String to,          // 끝 (HH:mm)
        BigDecimal unitPrice // 단가 원/kWh
) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    // Entity -> DTO 변환
    public static TariffPeriodResponse from(TariffPeriod tariffPeriod) {
        return new TariffPeriodResponse(
                tariffPeriod.getPeriodId(),
                tariffPeriod.getSeason().name(),
                toPeriodCode(tariffPeriod.getPeriodType()),
                toLabel(tariffPeriod.getPeriodType()),
                tariffPeriod.getStartTime().format(HH_MM),
                toEndText(tariffPeriod.getEndTime()),
                tariffPeriod.getUnitPrice()
        );
    }

    // 끝 시각 글자. 하루 끝(00:00 또는 23:59 이후)은 "24:00" (지금 상태 API의 요금표와 같은 표시)
    private static String toEndText(LocalTime end) {
        if (end.equals(LocalTime.MIDNIGHT) || !end.isBefore(LocalTime.of(23, 59))) {
            return "24:00";
        }
        return end.format(HH_MM);
    }

    // DB 코드를 화면 코드로 바꿈 (OFF_PEAK → LIGHT, MID_PEAK → MID, ON_PEAK → PEAK)
    private static String toPeriodCode(TariffPeriodType type) {
        return switch (type) {
            case OFF_PEAK -> "LIGHT";
            case MID_PEAK -> "MID";
            case ON_PEAK -> "PEAK";
        };
    }

    // 화면 표시 이름
    private static String toLabel(TariffPeriodType type) {
        return switch (type) {
            case OFF_PEAK -> "경부하";
            case MID_PEAK -> "중간부하";
            case ON_PEAK -> "최대부하";
        };
    }
}
