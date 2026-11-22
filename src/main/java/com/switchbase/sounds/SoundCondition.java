package com.switchbase.sounds;

/** 타건음 녹음 조건 (sound-compare-design §3, Q3). 코드 이름은 DB의 condition_code와 같다. */
public enum SoundCondition {

    CASE_OPEN("케이스 없음"),
    CASE_CLOSED("케이스 있음"),
    KEYBOARD_ON_DESK("책상 위 본체"),
    PHONE_MIC("휴대폰 녹음"),
    PRO_MIC("전문 마이크"),
    NOT_SURE("모름");

    private final String label;

    SoundCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
