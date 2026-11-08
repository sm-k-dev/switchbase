package com.switchbase.common;

import java.time.Duration;
import java.time.Instant;

/**
 * 보관 기간 30일 (review-collection-report §6-4, Q16, Q17).
 * 탈퇴한 회원의 글은 탈퇴 후 이 기간 동안만 목록에 보이고, 기간이 지나면 목록과 집계에서 빠지며 매일 완전 삭제된다.
 */
public final class Retention {

    public static final Duration PERIOD = Duration.ofDays(30);

    private Retention() {}

    /** 이 시각보다 이전에 탈퇴했으면 보관 기간이 끝난 것이다. */
    public static Instant cutoff(Instant now) {
        return now.minus(PERIOD);
    }
}
