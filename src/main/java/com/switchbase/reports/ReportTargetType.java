package com.switchbase.reports;

/** 신고 대상 종류 (Q9). */
public enum ReportTargetType {
    REVIEW("후기"), RATING("평가"), BUILD("빌드");

    private final String label;

    ReportTargetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
