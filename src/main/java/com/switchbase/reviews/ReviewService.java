package com.switchbase.reviews;

import com.switchbase.common.Retention;
import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.keyswitch.KeyboardSwitchRepository;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 후기 (review-collection-report §4.1, §5). 수정은 작성자, 삭제는 작성자 또는 관리자,
 * 숨김 해제는 작성자만(그 외 404). 도움됨은 본인 후기를 뺀 로그인 회원의 토글.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviews;
    private final ReviewVoteRepository votes;
    private final KeyboardSwitchRepository switches;
    private final MemberRepository members;

    public ReviewService(ReviewRepository reviews, ReviewVoteRepository votes,
                         KeyboardSwitchRepository switches, MemberRepository members) {
        this.reviews = reviews;
        this.votes = votes;
        this.switches = switches;
        this.members = members;
    }

    public Page<ReviewView> list(Long switchId, Long viewerId, boolean viewerAdmin, String sort, Pageable pageable) {
        if (!switches.existsById(switchId)) {
            throw new NoSuchElementException("스위치가 없습니다: " + switchId);
        }
        Long viewer = viewerId == null ? -1L : viewerId;
        Instant cutoff = Retention.cutoff(Instant.now());
        Page<Review> page = "helpful".equals(sort)
                ? reviews.findByHelpful(switchId, viewer, cutoff, pageable)
                : reviews.findRecent(switchId, viewer, cutoff, pageable);
        return page.map(r -> view(r, viewerId, viewerAdmin));
    }

    public Review load(Long id) {
        return reviews.findById(id).filter(r -> !r.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("후기가 없습니다: " + id));
    }

    /** 수정 화면. 작성자가 아니면 403. */
    public Review loadForEdit(Long id, Long viewerId) {
        Review r = load(id);
        if (!isAuthor(r, viewerId)) {
            throw new ReviewAccessDeniedException("작성자만 고칠 수 있어요");
        }
        return r;
    }

    @Transactional
    public Review create(Long switchId, Long memberId, String body, List<RecommendFor> recommended) {
        KeyboardSwitch sw = switches.findById(switchId)
                .orElseThrow(() -> new NoSuchElementException("스위치가 없습니다: " + switchId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));
        if (reviews.findFirstByKeyboardSwitchIdAndMemberIdAndDeletedAtIsNull(switchId, memberId).isPresent()) {
            throw new ReviewDuplicateException();
        }
        try {
            return reviews.saveAndFlush(new Review(sw, member, body, recommended));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ReviewDuplicateException();
        }
    }

    @Transactional
    public Review update(Long id, Long viewerId, Long expectedVersion, String body, List<RecommendFor> recommended) {
        Review r = loadForEdit(id, viewerId);
        if (expectedVersion == null || r.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 고쳤습니다: " + id);
        }
        r.apply(body, recommended);
        return reviews.saveAndFlush(r);
    }

    /** 삭제: 작성자 또는 관리자. 소프트 딜리트. 돌려주는 값은 스위치 id(리다이렉트용). */
    @Transactional
    public Long delete(Long id, Long viewerId, boolean viewerAdmin) {
        Review r = load(id);
        if (!viewerAdmin && !isAuthor(r, viewerId)) {
            throw new ReviewAccessDeniedException("작성자나 관리자만 삭제할 수 있어요");
        }
        Long switchId = r.getKeyboardSwitch().getId();
        r.softDelete(Instant.now());
        reviews.flush();
        return switchId;
    }

    /**
     * 도움됨 토글. 본인 후기는 누를 수 없다. 돌려주는 값은 스위치 id.
     * 후기 행을 잠가 직렬화하고, 도움됨 수는 투표 행 수로 다시 계산한다(증감하지 않는다, 리뷰 R2).
     * 중복 클릭은 ON CONFLICT로 같은 상태로 흡수한다.
     */
    @Transactional
    public Long toggleHelpful(Long id, Long viewerId) {
        Review r = reviews.findForUpdate(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("후기가 없습니다: " + id));
        if (isAuthor(r, viewerId)) {
            throw new IllegalArgumentException("본인 후기에는 도움됨을 누를 수 없어요");
        }
        if (votes.existsByReviewIdAndMemberId(id, viewerId)) {
            votes.deleteVote(id, viewerId);
        } else {
            votes.insertIgnore(id, viewerId);
        }
        reviews.flush();
        r.changeHelpfulCount((int) votes.countByReviewId(id));
        return r.getKeyboardSwitch().getId();
    }

    /** 숨김 해제. 작성자만 가능하고, 그 외(관리자 포함)에게는 404다. */
    @Transactional
    public Long unhide(Long id, Long viewerId) {
        Review r = load(id);
        if (!isAuthor(r, viewerId)) {
            throw new NoSuchElementException("후기가 없습니다: " + id);
        }
        if (r.isHidden()) {
            r.changeHidden(false);
        }
        return r.getKeyboardSwitch().getId();
    }

    /** 도움됨 수를 투표 행에서 다시 센다(탈퇴로 투표가 지워졌을 때). */
    @Transactional
    public void recountHelpful(Long id) {
        reviews.findById(id).filter(r -> !r.isDeleted())
                .ifPresent(r -> r.changeHelpfulCount((int) votes.countByReviewId(id)));
    }

    /** 신고 처리 결과로 숨김(관리자 경로에서만 호출). */
    @Transactional
    public void hideByReport(Long id) {
        load(id).changeHidden(true);
    }

    private static boolean isAuthor(Review r, Long viewerId) {
        return viewerId != null && Objects.equals(viewerId, r.getMember().getId());
    }

    private ReviewView view(Review r, Long viewerId, boolean viewerAdmin) {
        boolean mine = isAuthor(r, viewerId);
        boolean withdrawn = r.getMember().isWithdrawn();
        return new ReviewView(
                r.getId(),
                withdrawn ? "탈퇴한 회원" : r.getMember().getNickname(),
                r.getBody(),
                r.recommendations().stream().map(RecommendFor::getLabel).toList(),
                r.getHelpfulCount(),
                r.getCreatedAt(),
                mine,
                mine || viewerAdmin,
                viewerId != null && !mine,
                viewerId != null && votes.existsByReviewIdAndMemberId(r.getId(), viewerId),
                mine && r.isHidden());
    }
}
