package com.switchbase.reviews;

/** 후기 추천 대상 고정 선택지 (Q4, 여러 개 선택). */
public enum RecommendFor {
    BEGINNER("입문자"),
    QUIET("조용한 환경"),
    SMOOTH_LOVER("부드러운 타건을 좋아하는 사람"),
    TACTILE_LOVER("택타일을 좋아하는 사람");

    private final String label;

    RecommendFor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
