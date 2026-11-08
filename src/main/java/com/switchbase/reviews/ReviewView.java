package com.switchbase.reviews;

import java.time.Instant;
import java.util.List;

/** 후기 목록 뷰 (review-screens §3). 이메일 없음. 탈퇴 회원은 "탈퇴한 회원". */
public record ReviewView(
        Long id,
        String authorNickname,
        String body,
        List<String> recommendedLabels,
        int helpfulCount,
        Instant createdAt,
        boolean mine,
        boolean deletable,
        boolean canVote,
        boolean helpfulByMe,
        boolean hidden) {
}
