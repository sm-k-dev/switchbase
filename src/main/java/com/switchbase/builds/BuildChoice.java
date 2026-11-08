package com.switchbase.builds;

/** 고정 선택지 한 줄. code는 DB에 저장되는 값, label은 화면 표시 이름. */
public record BuildChoice(String code, String label) {
}
