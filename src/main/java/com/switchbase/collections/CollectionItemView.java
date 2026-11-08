package com.switchbase.collections;

import java.time.Instant;

/** 내 컬렉션 목록 한 줄 (review-screens §3). */
public record CollectionItemView(Long switchId, String switchName, String statusLabel, Instant createdAt) {
}
