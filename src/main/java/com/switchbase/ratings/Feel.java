package com.switchbase.ratings;

/** 타건감 코드와 표시 이름 (rating-design §3). */
public enum Feel {
    SMOOTH("부드러움"), CRISP("명확함"), CATCHY("걸림");

    private final String label;

    Feel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
