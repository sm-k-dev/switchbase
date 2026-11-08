package com.switchbase.collections;

/** 상세 화면의 토글 상태 (review-screens §3). */
public record CollectionState(boolean favorite, boolean owned, boolean tried) {
}
