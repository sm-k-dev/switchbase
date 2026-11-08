package com.switchbase.reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVote.Key> {

    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);

    long countByReviewId(Long reviewId);

    /** 중복 클릭으로 같은 행이 이미 있어도 오류 없이 넘어간다(리뷰 R2). */
    @Modifying
    @Query(value = "INSERT INTO review_vote (review_id, member_id) VALUES (:rid, :mid) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("rid") Long reviewId, @Param("mid") Long memberId);

    @Modifying
    @Query("delete from ReviewVote v where v.reviewId = :rid and v.memberId = :mid")
    int deleteVote(@Param("rid") Long reviewId, @Param("mid") Long memberId);

    @Query("select v.reviewId from ReviewVote v where v.memberId = :mid")
    java.util.List<Long> findReviewIdsByMember(@Param("mid") Long memberId);

    @Modifying
    @Query("delete from ReviewVote v where v.memberId = :mid")
    int deleteByMember(@Param("mid") Long memberId);
}
