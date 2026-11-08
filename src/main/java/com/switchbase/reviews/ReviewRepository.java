package com.switchbase.reviews;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 도움됨 토글 직렬화용 행 잠금(리뷰 R2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Review v where v.id = :id")
    Optional<Review> findForUpdate(@Param("id") Long id);

    /** 같은 회원의 살아 있는 후기 하나(스위치당 1건, Q2). */
    Optional<Review> findFirstByKeyboardSwitchIdAndMemberIdAndDeletedAtIsNull(Long switchId, Long memberId);

    /**
     * 목록: 삭제된 후기는 빼고, 숨김 후기는 작성자 본인에게만 보인다(`hidden = false OR 작성자 = 본인`).
     * 탈퇴 회원의 후기는 보관 기간(cutoff 이후 탈퇴)까지만 보인다.
     */
    @Query(value = "select v from Review v join fetch v.member m where v.keyboardSwitch.id = :sid "
            + "and v.deletedAt is null and (v.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff) "
            + "order by v.createdAt desc, v.id desc",
            countQuery = "select count(v) from Review v join v.member m where v.keyboardSwitch.id = :sid "
            + "and v.deletedAt is null and (v.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff)")
    Page<Review> findRecent(@Param("sid") Long switchId, @Param("viewer") Long viewerId,
                            @Param("cutoff") Instant cutoff, Pageable pageable);

    @Query(value = "select v from Review v join fetch v.member m where v.keyboardSwitch.id = :sid "
            + "and v.deletedAt is null and (v.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff) "
            + "order by v.helpfulCount desc, v.createdAt desc, v.id desc",
            countQuery = "select count(v) from Review v join v.member m where v.keyboardSwitch.id = :sid "
            + "and v.deletedAt is null and (v.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff)")
    Page<Review> findByHelpful(@Param("sid") Long switchId, @Param("viewer") Long viewerId,
                               @Param("cutoff") Instant cutoff, Pageable pageable);
}
