package com.switchbase.builds;

/** 고정 선택지 범위 밖 값. 화면에서는 해당 필드 오류로 보인다. */
public class BuildChoiceException extends IllegalArgumentException {

    private final BuildPart part;

    public BuildChoiceException(BuildPart part) {
        super("올바른 선택지를 고르세요");
        this.part = part;
    }

    public BuildPart getPart() {
        return part;
    }
}
