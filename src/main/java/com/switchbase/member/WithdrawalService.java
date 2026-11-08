package com.switchbase.member;

import com.switchbase.collections.CollectionItemRepository;
import com.switchbase.common.Retention;
import com.switchbase.keyswitch.SwitchRevisionRepository;
import com.switchbase.reports.ReportRepository;
import com.switchbase.reviews.ReviewRepository;
import com.switchbase.reviews.ReviewService;
import com.switchbase.reviews.ReviewVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 회원 탈퇴 (review-collection-report §6-4, §6-5 Q15·Q16·Q18·Q19).
 * 이메일·해시를 즉시 지우고, 이메일 해시로 30일 재가입을 막는다. 컬렉션과 도움됨은 즉시 지운다.
 * 평가·후기·빌드는 탈퇴 후 보관 기간 동안만 보이고, 기간이 지나면 RetentionService가 완전 삭제한다.
 */
@Service
public class WithdrawalService {

    private final MemberRepository members;
    private final SignupBlockRepository blocks;
    private final CollectionItemRepository collections;
    private final ReviewVoteRepository votes;
    private final ReviewRepository reviews;
    private final ReviewService reviewService;
    private final ReportRepository reports;
    private final SwitchRevisionRepository revisions;
    private final EmailVerificationTokenRepository tokens;

    public WithdrawalService(MemberRepository members, SignupBlockRepository blocks,
                             CollectionItemRepository collections, ReviewVoteRepository votes,
                             ReviewRepository reviews, ReviewService reviewService, ReportRepository reports,
                             SwitchRevisionRepository revisions, EmailVerificationTokenRepository tokens) {
        this.members = members;
        this.blocks = blocks;
        this.collections = collections;
        this.votes = votes;
        this.reviews = reviews;
        this.reviewService = reviewService;
        this.reports = reports;
        this.revisions = revisions;
        this.tokens = tokens;
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member m = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));
        if (m.isWithdrawn()) {
            throw new NoSuchElementException("이미 탈퇴한 회원이에요");
        }
        Instant now = Instant.now();
        Instant until = now.plus(Retention.PERIOD);

        if (m.getEmail() != null) {
            String hash = MemberService.sha256Hex(m.getEmail());
            blocks.findById(hash).ifPresentOrElse(
                    b -> b.extendTo(until),
                    () -> blocks.save(new SignupBlock(hash, until)));
        }

        collections.deleteByMember(memberId);

        List<Long> voted = votes.findReviewIdsByMember(memberId);
        votes.deleteByMember(memberId);
        for (Long reviewId : voted) {
            reviewService.recountHelpful(reviewId);
        }

        reports.disconnectReporter(memberId);
        revisions.renameEditor(memberId, "탈퇴한 회원");
        tokens.deleteByMember(memberId);

        m.withdraw(now);
    }
}
