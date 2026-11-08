package com.switchbase.reviews;

import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/** 도움됨 표시. 회원당 후기 하나에 한 줄. 토글은 행 추가와 삭제로 한다. */
@Entity
@Table(name = "review_vote")
@IdClass(ReviewVote.Key.class)
public class ReviewVote {

    @Id
    @Column(name = "review_id")
    private Long reviewId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    protected ReviewVote() {}

    ReviewVote(Long reviewId, Long memberId) {
        this.reviewId = reviewId;
        this.memberId = memberId;
    }

    public static class Key implements Serializable {
        private Long reviewId;
        private Long memberId;

        public Key() {}

        public Key(Long reviewId, Long memberId) {
            this.reviewId = reviewId;
            this.memberId = memberId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(reviewId, k.reviewId) && Objects.equals(memberId, k.memberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reviewId, memberId);
        }
    }
}
