package com.switchbase.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 신고 폼 (review-screens §3). */
public class ReportForm {

    @NotNull
    private ReportTargetType targetType;

    @NotNull
    private Long targetId;

    @NotNull(message = "사유를 골라 주세요.")
    private ReportReason reasonCode;

    @Size(max = 500, message = "메모는 500자 이하로 적어 주세요.")
    private String detail;

    public ReportTargetType getTargetType() { return targetType; }
    public void setTargetType(ReportTargetType targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public ReportReason getReasonCode() { return reasonCode; }
    public void setReasonCode(ReportReason reasonCode) { this.reasonCode = reasonCode; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
