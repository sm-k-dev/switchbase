package com.switchbase.common;

import com.switchbase.ratings.RatingStatsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 보관 기간이 지난 행을 완전 삭제한다 (review-collection-report Q17). 멱등이다.
 * 대상: 소프트 딜리트 후 30일이 지난 평가·후기·빌드, 탈퇴 후 30일이 지난 회원의 평가·후기·빌드, 만료된 재가입 차단.
 * 영향 받은 스위치의 집계는 같은 트랜잭션에서 다시 계산한다.
 */
@Service
public class RetentionService {

    private final JdbcTemplate jdbc;
    private final RatingStatsService stats;

    public RetentionService(JdbcTemplate jdbc, RatingStatsService stats) {
        this.jdbc = jdbc;
        this.stats = stats;
    }

    public record Result(int ratings, int reviews, int builds, int blocks) {
    }

    @Transactional
    public Result purge(Instant now) {
        Timestamp cutoff = Timestamp.from(Retention.cutoff(now));
        String expired = "(deleted_at < ? OR member_id IN (SELECT id FROM member WHERE deleted_at < ?))";

        List<Long> switchIds = jdbc.queryForList(
                "SELECT DISTINCT switch_id FROM rating WHERE " + expired, Long.class, cutoff, cutoff);
        int ratings = jdbc.update("DELETE FROM rating WHERE " + expired, cutoff, cutoff);
        int reviews = jdbc.update("DELETE FROM review WHERE " + expired, cutoff, cutoff);
        int builds = jdbc.update("DELETE FROM keyboard_build WHERE deleted_at < ? "
                + "OR member_id IN (SELECT id FROM member WHERE deleted_at < ?)", cutoff, cutoff);
        int blocks = jdbc.update("DELETE FROM signup_block WHERE expires_at < ?", Timestamp.from(now));

        for (Long switchId : switchIds) {
            stats.recompute(switchId);
        }
        return new Result(ratings, reviews, builds, blocks);
    }
}
