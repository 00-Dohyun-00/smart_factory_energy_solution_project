package com.smartfactory.energy.report.dto;

import java.time.LocalDate;

/**
 * 하루 방전량.
 */
public record ReportDailyDischarge(LocalDate date, Double kwh) {
}
