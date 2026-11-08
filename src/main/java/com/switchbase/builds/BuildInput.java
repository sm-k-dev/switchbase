package com.switchbase.builds;

/** 등록·수정 입력값. 빈 문자열은 서비스에서 NULL로 바꾼다. */
public record BuildInput(String name, String housingMaterial, String plateMaterial,
                         String mountType, String keycapMaterial, String memo) {
}
