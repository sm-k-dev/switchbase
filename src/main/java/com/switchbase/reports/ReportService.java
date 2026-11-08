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

    /** 대상의 위치와 상태: 작성자, 상세 링크, 숨김 여부에 쓴다. */
    record Target(Long ownerId, Long switchId, Long buildId, boolean hidden) {
        String href() {
            if (switchId != null) return "/switches/" + switchId;
            if (buildId != null) return "/builds/" + buildId;
            return null;
        }
    }

    /** 삭제된 대상은 없는 것으로 본다. 숨김 대상은 관리자 목록에서 상태를 보여 주려고 그대로 돌려준다. */
    Optional<Target> resolve(ReportTargetType type, Long id) {
        return switch (type) {
            case REVIEW -> reviews.findById(id).filter(r -> !r.isDeleted())
                    .map(r -> new Target(r.getMember().getId(), r.getKeyboardSwitch().getId(), null, r.isHidden()));
            case RATING -> ratings.findById(id).filter(r -> !r.isDeleted())
                    .map(r -> new Target(r.getMember().getId(), r.getKeyboardSwitch().getId(), null, r.isHidden()));
            case BUILD -> builds.findById(id).filter(b -> b.getDeletedAt() == null)
                    .map(b -> new Target(b.getMember().getId(), null, b.getId(), b.isHidden()));
        };
    }

    /** 접수. 본인 대상과 중복은 거부한다. 돌려주는 값은 대상 상세 링크(없으면 null). */
    @Transactional
    public String create(Long reporterId, ReportTargetType type, Long targetId, ReportReason reason, String detail) {
        // 숨김 대상(후기·평가·빌드)은 접수하지 않는다(레코드를 만들지 않는다). 삭제된 대상도 같다.
        Target target = resolve(type, targetId)
                .filter(t -> !t.hidden())
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
            // 숨김 처리는 살아 있고 아직 숨겨지지 않은 빌드에만 한다
            if (report.getTargetType() == ReportTargetType.BUILD) {
                Target current = resolve(ReportTargetType.BUILD, report.getTargetId())
                        .orElseThrow(() -> new NoSuchElementException("대상이 없어요: " + report.getTargetId()));
                if (current.hidden()) {
                    throw new IllegalArgumentException("이미 숨겨진 빌드예요");
                }
            }
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
        String state = target.isEmpty() ? "대상 없음" : (target.get().hidden() ? "숨김" : "");
        Member reporter = r.getReporter();
        String reporterLabel = reporter == null ? "탈퇴한 회원" : reporter.getNickname();
        String resolutionLabel = switch (r.getResolution() == null ? "" : r.getResolution()) {
            case "HIDDEN" -> "숨김";
            case "DISMISSED" -> "기각";
            default -> "";
        };
        return new ReportView(r.getId(), r.getCreatedAt(), href, r.getTargetType().getLabel(),
                r.getReason().getLabel(), r.getDetail(), reporterLabel, r.getStatus().name(), resolutionLabel, state);
    }
}
