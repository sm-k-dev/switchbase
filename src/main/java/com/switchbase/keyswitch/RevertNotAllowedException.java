package com.switchbase.keyswitch;

public class RevertNotAllowedException extends RuntimeException {

    private final RevertBlock block;

    public RevertNotAllowedException(RevertBlock block) {
        super(block.name());
        this.block = block;
    }

    public RevertBlock getBlock() {
        return block;
    }
}
