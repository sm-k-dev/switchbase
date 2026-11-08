package com.switchbase.member;

/** 이메일 또는 닉네임이 이미 쓰이는 중. field는 폼 필드 이름("email" 또는 "nickname"). */
public class DuplicateMemberException extends RuntimeException {

    private final String field;

    public DuplicateMemberException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
