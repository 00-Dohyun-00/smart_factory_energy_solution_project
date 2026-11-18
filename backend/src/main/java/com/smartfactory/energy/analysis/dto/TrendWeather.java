package com.smartfactory.energy.analysis.dto;

import java.time.LocalDate;

/**
 * 날짜별 날씨 1개. text는 그날 가장 많이 나온 하늘 상태 (예: 맑음).
 */
public record TrendWeather(LocalDate date, String text) {
}
