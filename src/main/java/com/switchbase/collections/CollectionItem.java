package com.switchbase.collections;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 회원 × 스위치 × 상태 한 줄. 해제는 하드 삭제한다(Q18). */
@Entity
@Table(name = "collection_item")
@IdClass(CollectionItem.Key.class)
public class CollectionItem {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "switch_id")
    private Long switchId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CollectionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CollectionItem() {}

    CollectionItem(Long memberId, Long switchId, CollectionStatus status) {
        this.memberId = memberId;
        this.switchId = switchId;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getMemberId() { return memberId; }
    public Long getSwitchId() { return switchId; }
    public CollectionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public static class Key implements Serializable {
        private Long memberId;
        private Long switchId;
        private CollectionStatus status;

        public Key() {}

        public Key(Long memberId, Long switchId, CollectionStatus status) {
            this.memberId = memberId;
            this.switchId = switchId;
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(memberId, k.memberId)
                    && Objects.equals(switchId, k.switchId) && status == k.status;
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberId, switchId, status);
        }
    }
}
