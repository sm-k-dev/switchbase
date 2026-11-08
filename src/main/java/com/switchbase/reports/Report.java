package com.switchbase.reports;

import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.time.Instant;

/** 신고 1건 (Q10: 회원당 대상당 1회). 신고자 탈퇴 시 연결만 끊고 처리 기록은 남긴다. */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_member_id")
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    /** HIDDEN 또는 DISMISSED */
    @Column(name = "resolution", length = 20)
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_member_id")
    private Member handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {}

    Report(Member reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.RECEIVED;
        this.createdAt = Instant.now();
    }

    void resolve(Member admin, boolean hide) {
        this.status = hide ? ReportStatus.RESOLVED : ReportStatus.DISMISSED;
        this.resolution = hide ? "HIDDEN" : "DISMISSED";
        this.handledBy = admin;
        this.handledAt = Instant.now();
    }

    void disconnectReporter() {
        this.reporter = null;
    }

    public Long getId() { return id; }
    public Member getReporter() { return reporter; }
    public ReportTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public ReportReason getReason() { return reason; }
    public String getDetail() { return detail; }
    public ReportStatus getStatus() { return status; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
}
