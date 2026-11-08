package com.switchbase.collections;

/** 컬렉션 상태 (Q7: 각각 독립 토글). */
public enum CollectionStatus {
    FAVORITE("즐겨찾기"), OWNED("보유"), TRIED("써봤음");

    private final String label;

    CollectionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
