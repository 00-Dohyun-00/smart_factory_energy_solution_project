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

    // 적용되거나(APPLIED) 자동 처리된(AUTO) 것 중 가장 최근 1건. 시뮬레이터가 "지금 ESS에 낼 명령"을
    // 이 값으로 판단합니다 (메모리 대신 DB를 보므로, 적용 요청이 다른 서버로 가도 똑같이 따릅니다)
    Optional<Recommendation> findFirstByStatusInOrderByRecommendedAtDescRecommendationIdDesc(
            List<RecommendationStatus> statuses);
}
