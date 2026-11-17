package com.smartfactory.energy.recommendation;

// recommendation.status 의 CHECK 제약과 동일한 값: 대기중 / 적용 / 자동 / 넘김 / 만료
public enum RecommendationStatus {
    PENDING, APPLIED, AUTO, SKIPPED, EXPIRED
}
