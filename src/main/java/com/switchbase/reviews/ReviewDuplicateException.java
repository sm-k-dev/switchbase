package com.switchbase.reviews;

/** 같은 스위치에 이미 후기가 있다(스위치당 1건, Q2). */
public class ReviewDuplicateException extends RuntimeException {

    public ReviewDuplicateException() {
        super("이 스위치에는 이미 후기를 남겼어요. 수정 화면에서 고쳐 주세요.");
    }
}
