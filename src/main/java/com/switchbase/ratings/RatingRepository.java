package com.switchbase.ratings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    /** 목록: 최신순. 작성자 닉네임을 같이 읽는다. */
    @Query(value = "select r from Rating r join fetch r.member where r.keyboardSwitch.id = :sid "
            + "order by r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Rating r where r.keyboardSwitch.id = :sid")
    Page<Rating> findBySwitch(@Param("sid") Long switchId, Pageable pageable);

    /** 빌드 null이면 빌드 없이 평가한 건을 찾는다. */
    Optional<Rating> findByKeyboardSwitchIdAndMemberIdAndBuildId(Long switchId, Long memberId, Long buildId);

    @Query("select new com.switchbase.ratings.RatingSummary("
            + "avg(r.soundLevel), avg(r.fatigueLevel), avg(r.starRating), count(distinct r.member.id), count(r)) "
            + "from Rating r where r.keyboardSwitch.id = :sid")
    RatingSummary summaryOf(@Param("sid") Long switchId);
}
