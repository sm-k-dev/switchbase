package com.switchbase.member;

/** 인증 링크 전달 수단. 지금은 로그 출력만 하고, SMTP는 구현체만 바꿔 붙인다. */
public interface VerificationNotifier {

    void send(Member member, String verifyUrl);
}
