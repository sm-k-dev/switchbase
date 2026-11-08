package com.switchbase.ratings;

/**
 * 요약(실시간 조회, rating-screens §2). 평균은 평가 건 기준. sampleCount = 서로 다른 회원 수(N명),
 * ratingCount = 평가 건수(M건). 평균은 평가가 없으면 null.
 */
public record RatingSummary(Double avgSound, Double avgFatigue, Double avgStar, Long sampleCount, Long ratingCount) {
}
