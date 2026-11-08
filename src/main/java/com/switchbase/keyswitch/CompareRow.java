package com.switchbase.keyswitch;

/** 비교·되돌리기 화면의 한 줄. changed는 두 값이 다를 때 true. */
public record CompareRow(String label, String fromValue, String toValue, boolean changed) {
}
