package com.switchbase.reports;

/** 신고 사유 고정 선택지 (Q11). 자유 메모는 detail에 쓴다. */
public enum ReportReason {
    SPAM("광고·스팸"), ABUSE("욕설·비방"), WRONG_INFO("잘못된 정보"), OTHER("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
