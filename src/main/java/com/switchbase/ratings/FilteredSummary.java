package com.switchbase.ratings;

import com.switchbase.builds.BuildPart;

/**
 * 빌드 조건(하우징, 윤활)으로 좁힌 체감 요약 (sound-compare-design §5.4).
 * 표본이 MIN_MEMBERS 미만이면 평균을 숨긴다(Q6). 비교 화면과 상세 화면이 이 규칙을 같이 쓴다.
 */
public record FilteredSummary(Double sound, Double fatigue, Double star,
                              long members, long ratings, boolean averagesHidden) {

    public static final int MIN_MEMBERS = 3;

    static FilteredSummary of(RatingSummary s) {
        long members = s.sampleCount() == null ? 0L : s.sampleCount();
        long ratings = s.ratingCount() == null ? 0L : s.ratingCount();
        boolean hidden = members < MIN_MEMBERS;
        return new FilteredSummary(
                hidden ? null : s.avgSound(),
                hidden ? null : s.avgFatigue(),
                hidden ? null : s.avgStar(),
                members, ratings, hidden);
    }

    /** 허용된 하우징 코드만 돌려준다. 빈 값이나 모르는 코드는 조건 없음(null). */
    public static String housingOrNull(String code) {
        return code != null && !code.isBlank() && BuildPart.HOUSING.accepts(code) ? code : null;
    }

    /** "true"·"false"만 받는다. 그 밖은 조건 없음(null). */
    public static Boolean lubedOrNull(String value) {
        return "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : null;
    }
}
