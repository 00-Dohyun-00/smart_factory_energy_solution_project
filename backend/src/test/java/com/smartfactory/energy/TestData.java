package com.smartfactory.energy;

import com.smartfactory.energy.ess.EssStatus;
import com.smartfactory.energy.ess.EssStatusMinute;
import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.SitePowerMinute;
import com.smartfactory.energy.recommendation.Recommendation;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.setting.Setting;
import com.smartfactory.energy.setting.SettingGroup;
import com.smartfactory.energy.tariff.TariffPeriod;
import com.smartfactory.energy.tariff.TariffPeriodType;
import com.smartfactory.energy.tariff.TariffSeason;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트에서 쓰는 더미데이터를 만드는 곳.
 * DB 없이 Service만 시험하려고, Repository가 돌려줄 Entity를 여기서 직접 만듦.
 */
public class TestData {

    // 1분 전력 기록 1줄
    public static SitePowerMinute power(LocalDateTime ts, double gridKw, double solarKw, double essKw, double loadKw) {
        return new SitePowerMinute(ts, bd(gridKw), bd(solarKw), bd(essKw), bd(loadKw));
    }

    // start부터 1분 간격으로 count줄 (한전 구매 gridKw, 나머지는 0)
    public static List<SitePowerMinute> powers(LocalDateTime start, int count, double gridKw) {
        List<SitePowerMinute> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(power(start.plusMinutes(i), gridKw, 0, 0, gridKw));
        }
        return result;
    }

    // ESS 1분 상태 1줄
    public static EssStatusMinute ess(LocalDateTime ts, double chargeKwh, double dischargeKwh, double soc) {
        return new EssStatusMinute(ts, bd(0), bd(chargeKwh), bd(dischargeKwh), bd(soc), bd(25), EssStatus.IDLE);
    }

    // demand_interval_15m 뷰 1줄. 뷰 Entity는 생성자가 없어서 값을 직접 넣음
    public static DemandInterval15m interval(LocalDateTime start, double actualKw, double noEssKw, long minuteCount) {
        DemandInterval15m d = BeanUtils.instantiateClass(DemandInterval15m.class);
        ReflectionTestUtils.setField(d, "intervalStart", start);
        ReflectionTestUtils.setField(d, "actualAvgKw", bd(actualKw));
        ReflectionTestUtils.setField(d, "noEssAvgKw", bd(noEssKw));
        ReflectionTestUtils.setField(d, "reducedKw", bd(noEssKw - actualKw));
        ReflectionTestUtils.setField(d, "minuteCount", minuteCount);
        return d;
    }

    // 추천 1줄. 번호(id)는 DB가 매기는 값이라 직접 넣음
    public static Recommendation recommendation(long id, LocalDateTime recommendedAt, LocalDateTime intervalStart,
                                                RecommendationAction action, RecommendationReasonType reasonType,
                                                RecommendationStatus status, double targetPeakKw) {
        Recommendation r = new Recommendation(recommendedAt, intervalStart, action, bd(500), reasonType,
                "테스트 추천", bd(1900), bd(targetPeakKw), status, null, null);
        ReflectionTestUtils.setField(r, "recommendationId", id);
        return r;
    }

    // 기준값 1줄
    public static Setting setting(String key, String label, String value) {
        return new Setting(key, SettingGroup.PEAK_TARIFF, label, value, "", LocalDateTime.of(2026, 11, 1, 0, 0), "운영자");
    }

    // 요금 시간대 1줄
    public static TariffPeriod tariff(TariffSeason season, TariffPeriodType type, String start, String end, double price) {
        return new TariffPeriod(season, type, LocalTime.parse(start), LocalTime.parse(end), bd(price));
    }

    // 계절 하나의 요금표 (경부하 0~8, 중간 8~11, 최대 11~12, 중간 12~13, 최대 13~17, 중간 17~23, 경부하 23~23:59:59. DB는 시작 < 끝 제약이 있어서 하루 끝을 23:59:59로 적음)
    public static List<TariffPeriod> dayTariffs(TariffSeason season) {
        return List.of(
                tariff(season, TariffPeriodType.OFF_PEAK, "00:00", "08:00", 100),
                tariff(season, TariffPeriodType.MID_PEAK, "08:00", "11:00", 150),
                tariff(season, TariffPeriodType.ON_PEAK, "11:00", "12:00", 200),
                tariff(season, TariffPeriodType.MID_PEAK, "12:00", "13:00", 150),
                tariff(season, TariffPeriodType.ON_PEAK, "13:00", "17:00", 200),
                tariff(season, TariffPeriodType.MID_PEAK, "17:00", "23:00", 150),
                tariff(season, TariffPeriodType.OFF_PEAK, "23:00", "23:59:59", 100)
        );
    }

    public static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
