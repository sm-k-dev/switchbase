package com.switchbase.reports;

import com.switchbase.builds.BuildService;
import com.switchbase.builds.KeyboardBuild;
import com.switchbase.builds.KeyboardBuildRepository;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import com.switchbase.ratings.Rating;
import com.switchbase.ratings.RatingRepository;
import com.switchbase.ratings.RatingService;
import com.switchbase.reviews.Review;
import com.switchbase.reviews.ReviewRepository;
import com.switchbase.reviews.ReviewService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 신고 (review-collection-report §4.3, Q9~Q13). 접수는 기록만 하고, 숨김은 관리자 처리 때 한다(Q13).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reports;
    private final MemberRepository members;
    private final ReviewRepository reviews;
    private final RatingRepository ratings;
    private final KeyboardBuildRepository builds;
    private final ReviewService reviewService;
    private final RatingService ratingService;
    private final BuildService buildService;

    public ReportService(ReportRepository reports, MemberRepository members, ReviewRepository reviews,
                         RatingRepository ratings, KeyboardBuildRepository builds, ReviewService reviewService,
                         RatingService ratingService, BuildService buildService) {
        this.reports = reports;
        this.members = members;
        this.reviews = reviews;
        this.ratings = ratings;
        this.builds = builds;
        this.reviewService = reviewService;
        this.ratingService = ratingService;
        this.buildService = buildService;
    }

    /** 대상의 위치: 작성자와 상세 링크에 쓴다. */
    record Target(Long ownerId, Long switchId, Long buildId) {
        String href() {
            if (switchId != null) return "/switches/" + switchId;
            if (buildId != null) return "/builds/" + buildId;
            return null;
        }
    }

    Optional<Target> resolve(ReportTargetType type, Long id) {
        return switch (type) {
            case REVIEW -> reviews.findById(id).filter(r -> !r.isDeleted())
                    .map(r -> new Target(r.getMember().getId(), r.getKeyboardSwitch().getId(), null));
            case RATING -> ratings.findById(id).filter(r -> !r.isDeleted())
                    .map(r -> new Target(r.getMember().getId(), r.getKeyboardSwitch().getId(), null));
            case BUILD -> builds.findById(id)
                    .map(b -> new Target(b.getMember().getId(), null, b.getId()));
        };
    }

    /** 접수. 본인 대상과 중복은 거부한다. 돌려주는 값은 대상 상세 링크(없으면 null). */
    @Transactional
    public String create(Long reporterId, ReportTargetType type, Long targetId, ReportReason reason, String detail) {
        Target target = resolve(type, targetId)
                .orElseThrow(() -> new NoSuchElementException("신고할 대상이 없어요: " + type + " " + targetId));
        if (Objects.equals(target.ownerId(), reporterId)) {
            throw new IllegalArgumentException("본인 글은 신고할 수 없어요");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, targetId)) {
            throw new ReportDuplicateException();
        }
        Member reporter = members.findById(reporterId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + reporterId));
        try {
            reports.saveAndFlush(new Report(reporter, type, targetId, reason, detail));
        } catch (DataIntegrityViolationException e) {
            throw new ReportDuplicateException();
        }
        return target.href();
    }

    /** 관리자 처리: hide면 대상을 숨기고 RESOLVED, 아니면 DISMISSED. 처리 기록을 남긴다. */
    @Transactional
    public void resolve(Long reportId, Long adminId, boolean hide) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("신고가 없습니다: " + reportId));
        if (report.getStatus() != ReportStatus.RECEIVED) {
            throw new IllegalArgumentException("이미 처리한 신고예요");
        }
        Member admin = members.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + adminId));
        if (hide) {
            switch (report.getTargetType()) {
                case REVIEW -> reviewService.hideByReport(report.getTargetId());
                case RATING -> ratingService.hideByReport(report.getTargetId());
                case BUILD -> buildService.setHidden(report.getTargetId(), true);
            }
        }
        report.resolve(admin, hide);
    }

    public Page<ReportView> list(ReportStatus status, Pageable pageable) {
        return reports.findByStatusOrderByCreatedAtAsc(status, pageable).map(this::view);
    }

    private ReportView view(Report r) {
        Optional<Target> target = resolve(r.getTargetType(), r.getTargetId());
        String href = target.map(Target::href).orElse(null);
        Member reporter = r.getReporter();
        String reporterLabel = reporter == null ? "탈퇴한 회원" : reporter.getNickname();
        String resolutionLabel = switch (r.getResolution() == null ? "" : r.getResolution()) {
            case "HIDDEN" -> "숨김";
            case "DISMISSED" -> "기각";
            default -> "";
        };
        return new ReportView(r.getId(), r.getCreatedAt(), href, r.getTargetType().getLabel(),
                r.getReason().getLabel(), r.getDetail(), reporterLabel, r.getStatus().name(), resolutionLabel);
    }
}
