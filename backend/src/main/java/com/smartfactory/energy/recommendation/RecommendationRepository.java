package com.smartfactory.energy.recommendation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    // start 이상 ~ end 미만 기간에 나온 추천을 최신 순으로 조회
    @Query("select r from Recommendation r"
            + " where r.recommendedAt >= :start and r.recommendedAt < :end"
            + " order by r.recommendedAt desc")
    List<Recommendation> findInPeriod(LocalDateTime start, LocalDateTime end);

    // 가장 최근에 나온 추천 1건
    Optional<Recommendation> findFirstByOrderByRecommendedAtDescRecommendationIdDesc();
}
