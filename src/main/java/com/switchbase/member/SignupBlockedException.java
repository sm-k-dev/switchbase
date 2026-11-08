package com.switchbase.member;

/** 탈퇴 후 보관 기간 안의 이메일로 가입하려고 했다(Q15). */
public class SignupBlockedException extends RuntimeException {

    public SignupBlockedException() {
        super("탈퇴 후 30일 동안은 같은 이메일로 다시 가입할 수 없어요.");
    }
}
