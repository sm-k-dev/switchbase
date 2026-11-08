package com.switchbase.reports;

import java.time.Instant;

/** 관리자 신고 목록 한 줄 (review-screens §3). 대상이 없으면 targetHref는 null, targetState는 "대상 없음"·"숨김"·"" 중 하나. */
public record ReportView(
        Long id,
        Instant createdAt,
        String targetHref,
        String targetLabel,
        String reasonLabel,
        String detail,
        String reporterLabel,
        String status,
        String resolutionLabel,
        String targetState) {
}
