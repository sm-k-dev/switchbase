package com.switchbase.ratings;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** 스위치별 집계 행 (stats-design §3). 평가가 바뀔 때마다 rating에서 다시 계산해 채운다. */
@Entity
@Table(name = "switch_rating_stats")
public class SwitchRatingStats {

    @Id
    @Column(name = "switch_id")
    private Long switchId;

    @Column(name = "avg_sound", precision = 4, scale = 2)
    private BigDecimal avgSound;

    @Column(name = "avg_fatigue", precision = 4, scale = 2)
    private BigDecimal avgFatigue;

    @Column(name = "avg_star", precision = 4, scale = 2)
    private BigDecimal avgStar;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SwitchRatingStats() {}

    SwitchRatingStats(Long switchId) {
        this.switchId = switchId;
        this.updatedAt = Instant.now();
    }

    /** rating에서 방금 계산한 값으로 덮어쓴다. 평가가 없으면 평균은 NULL, 건수는 0. */
    void replace(RatingSummary s) {
        this.avgSound = round(s.avgSound());
        this.avgFatigue = round(s.avgFatigue());
        this.avgStar = round(s.avgStar());
        this.memberCount = s.sampleCount() == null ? 0 : s.sampleCount().intValue();
        this.ratingCount = s.ratingCount() == null ? 0 : s.ratingCount().intValue();
        this.updatedAt = Instant.now();
    }

    RatingSummary toSummary() {
        return new RatingSummary(toDouble(avgSound), toDouble(avgFatigue), toDouble(avgStar),
                (long) memberCount, (long) ratingCount);
    }

    private static BigDecimal round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public Long getSwitchId() { return switchId; }
    public int getMemberCount() { return memberCount; }
    public int getRatingCount() { return ratingCount; }
}
