package com.switchbase.ratings;

/** 같은 (스위치, 빌드) 조합의 평가가 이미 있다(Q2). */
public class RatingDuplicateException extends RuntimeException {

    public RatingDuplicateException() {
        super("이미 이 스위치에 같은 빌드(또는 빌드 없음)로 남긴 평가가 있어요. 수정 화면에서 고쳐 주세요.");
    }
}
