package com.switchbase.builds;

public class BuildLimitExceededException extends RuntimeException {

    public BuildLimitExceededException() {
        super("빌드는 한 사람당 20개까지 등록할 수 있어요");
    }
}
