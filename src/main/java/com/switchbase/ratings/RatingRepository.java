package com.switchbase.ratings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /**
     * 목록: 최신순. 삭제된 평가는 빼고, 숨김 평가는 작성자에게만 보이며,
     * 탈퇴 회원의 평가는 보관 기간(cutoff 이후 탈퇴)까지만 보인다.
     */
    @Query(value = "select r from Rating r join fetch r.member m where r.keyboardSwitch.id = :sid "
            + "and r.deletedAt is null and (r.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff) "
            + "order by r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Rating r join r.member m where r.keyboardSwitch.id = :sid "
            + "and r.deletedAt is null and (r.hidden = false or m.id = :viewer) "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff)")
    Page<Rating> findBySwitch(@Param("sid") Long switchId, @Param("viewer") Long viewerId,
                              @Param("cutoff") Instant cutoff, Pageable pageable);

    /** 같은 (스위치, 회원, 빌드)의 살아 있는 평가 하나. 빌드 null이면 빌드 없이 평가한 건을 찾는다. */
    Optional<Rating> findFirstByKeyboardSwitchIdAndMemberIdAndBuildIdAndDeletedAtIsNull(
            Long switchId, Long memberId, Long buildId);

    /**
     * 집계 식(stats-design, review-collection-report Q14·Q16): 삭제·숨김 평가와
     * 탈퇴 후 보관 기간이 지난 회원의 평가는 빼고 센다.
     */
    @Query("select new com.switchbase.ratings.RatingSummary("
            + "avg(r.soundLevel), avg(r.fatigueLevel), avg(r.starRating), count(distinct r.member.id), count(r)) "
            + "from Rating r join r.member m where r.keyboardSwitch.id = :sid "
            + "and r.deletedAt is null and r.hidden = false "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff)")
    RatingSummary summaryOf(@Param("sid") Long switchId, @Param("cutoff") Instant cutoff);

    /**
     * 빌드 조건으로 좁힌 요약(sound-compare-design §5.4). 빌드가 없는 평가와 삭제된 빌드의 평가는 빠진다.
     * housing·lubed가 null이면 그 조건은 걸지 않는다. 실시간 조회다.
     */
    @Query("select new com.switchbase.ratings.RatingSummary("
            + "avg(r.soundLevel), avg(r.fatigueLevel), avg(r.starRating), count(distinct r.member.id), count(r)) "
            + "from Rating r join r.member m join r.build b where r.keyboardSwitch.id = :sid "
            + "and r.deletedAt is null and r.hidden = false and b.deletedAt is null "
            + "and (m.deletedAt is null or m.deletedAt > :cutoff) "
            + "and (:housing is null or b.housingMaterial = :housing) "
            + "and (:lubed is null or r.lubed = :lubed)")
    RatingSummary summaryWithBuild(@Param("sid") Long switchId, @Param("cutoff") Instant cutoff,
                                   @Param("housing") String housing, @Param("lubed") Boolean lubed);
}
