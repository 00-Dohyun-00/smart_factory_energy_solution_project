package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.power.DemandInterval15m;
import com.smartfactory.energy.power.DemandInterval15mRepository;
import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    // 한 번에 조회할 수 있는 최대 기간 (일)
    private static final int MAX_DAYS = 62;

    private final RecommendationRepository recommendationRepository;
    private final DemandInterval15mRepository demandInterval15mRepository;

    // 기간 안의 추천 이력 (최신 순)
    public List<RecommendationResponse> findInPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        // 2026-11-14 ~ 2026-11-15 → 11-14 00:00 이상 ~ 11-16 00:00 미만
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // 15분 구간 시작 시각 → 실제 피크. 기간 끝 직전 추천은 다음 구간을 볼 수 있어서 15분 더 읽음
        Map<LocalDateTime, BigDecimal> resultPeaks = new HashMap<>();
        for (DemandInterval15m d : demandInterval15mRepository.findInPeriod(start, end.plusMinutes(15))) {
            // 15분이 다 찬 구간만 결과로 봄 (진행 중인 구간은 아직 결과 없음)
            if (d.getMinuteCount() != null && d.getMinuteCount() >= 15) {
                resultPeaks.put(d.getIntervalStart(), d.getActualAvgKw());
            }
        }

        return recommendationRepository.findInPeriod(start, end)
                .stream()
                .map(r -> RecommendationResponse.from(r, resultPeaks.get(r.getIntervalStart())))
                .toList();
    }
}
