package com.switchbase.reports;

public class ReportDuplicateException extends RuntimeException {

    public ReportDuplicateException() {
        super("이미 신고한 대상이에요");
    }
}
