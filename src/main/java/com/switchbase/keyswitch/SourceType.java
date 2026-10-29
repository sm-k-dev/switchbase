package com.switchbase.keyswitch;

public enum SourceType {
    OFFICIAL("제조사 공식"),
    RETAILER("판매처"),
    REVIEW("리뷰"),
    COMMUNITY("커뮤니티");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
