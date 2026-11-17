package com.smartfactory.energy.recommendation;

import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    // 기간 안의 추천 이력 (최신 순)
    public List<RecommendationResponse> findInPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜가 끝 날짜보다 늦습니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new IllegalArgumentException("기간은 최대 " + MAX_DAYS + "일까지 조회할 수 있습니다.");
        }

        // 2026-11-14 ~ 2026-11-15 → 11-14 00:00 이상 ~ 11-16 00:00 미만
        return recommendationRepository.findInPeriod(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(RecommendationResponse::from)
                .toList();
    }
}
