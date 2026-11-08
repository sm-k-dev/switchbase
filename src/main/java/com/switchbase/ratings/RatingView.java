package com.switchbase.ratings;

import java.time.Instant;
import java.util.List;

/**
 * 목록용 뷰 (rating-screens §2). 이메일 없음. 빌드 이름 없음(buildLinked만, Q6).
 * mine은 수정 버튼용, deletable은 삭제 버튼용(작성자 또는 관리자, 서버가 계산).
 */
public record RatingView(
        Long id,
        String authorNickname,
        int starRating,
        int soundLevel,
        String soundTone,
        String feel,
        int fatigueLevel,
        String lubedLabel,
        List<String> modTags,
        boolean buildLinked,
        Instant createdAt,
        boolean mine,
        boolean deletable) {
}
