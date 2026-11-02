package com.switchbase.keyswitch;

import java.util.Locale;

/** 이름 비교용 규칙. 대소문자와 띄어쓰기를 무시한다. "저 월백" == "저월백" */
public final class Names {

    private Names() {
    }

    public static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}