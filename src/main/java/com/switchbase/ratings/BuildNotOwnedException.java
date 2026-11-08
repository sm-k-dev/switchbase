package com.switchbase.ratings;

/** 남의 빌드를 평가에 붙이려 했다. */
public class BuildNotOwnedException extends RuntimeException {

    public BuildNotOwnedException() {
        super("본인 빌드만 연결할 수 있어요");
    }
}
