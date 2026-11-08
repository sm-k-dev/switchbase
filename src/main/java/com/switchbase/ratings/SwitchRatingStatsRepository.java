package com.switchbase.ratings;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SwitchRatingStatsRepository extends JpaRepository<SwitchRatingStats, Long> {

    /** 집계 행이 없으면 빈 행을 만든다. 동시에 만들어도 한쪽은 아무것도 하지 않는다. */
    @Modifying
    @Query(value = "INSERT INTO switch_rating_stats (switch_id, member_count, rating_count, updated_at) "
            + "VALUES (:id, 0, 0, now()) ON CONFLICT (switch_id) DO NOTHING", nativeQuery = true)
    int ensureRow(@Param("id") Long switchId);

    /** 같은 스위치의 집계 갱신을 직렬화한다. 잠금을 기다린 뒤 rating을 다시 읽으므로 두 건 모두 반영된다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SwitchRatingStats s where s.switchId = :id")
    Optional<SwitchRatingStats> findForUpdate(@Param("id") Long switchId);
}
