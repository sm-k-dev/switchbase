package com.switchbase.collections;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionItemRepository extends JpaRepository<CollectionItem, CollectionItem.Key> {

    boolean existsByMemberIdAndSwitchIdAndStatus(Long memberId, Long switchId, CollectionStatus status);

    Page<CollectionItem> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

    Page<CollectionItem> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, CollectionStatus status,
                                                                     Pageable pageable);

    /** 같은 상태가 이미 있어도 오류 없이 넘어간다(리뷰 R3). */
    @Modifying
    @Query(value = "INSERT INTO collection_item (member_id, switch_id, status, created_at) "
            + "VALUES (:mid, :sid, :status, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIgnore(@Param("mid") Long memberId, @Param("sid") Long switchId, @Param("status") String status);

    @Modifying
    @Query("delete from CollectionItem c where c.memberId = :mid and c.switchId = :sid and c.status = :status")
    int deleteOne(@Param("mid") Long memberId, @Param("sid") Long switchId, @Param("status") CollectionStatus status);

    @Modifying
    @Query("delete from CollectionItem c where c.memberId = :mid")
    int deleteByMember(@Param("mid") Long memberId);
}
