package com.switchbase.ratings;

/** 소리 성향 코드와 표시 이름 (rating-design §3). */
public enum SoundTone {
    CLACKY("도각"), POPPY("쪅"), HEAVY("묵직"), DULL("둔탁");

    private final String label;

    SoundTone(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
