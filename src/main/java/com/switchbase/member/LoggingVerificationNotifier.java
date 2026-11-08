package com.switchbase.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 개발용. 운영(prod)에서는 꺼진다(사전 리뷰 M2). SMTP 구현체가 prod에서 이 자리를 대신한다. */
@Component
@Profile("!prod")
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationNotifier.class);

    @Override
    public void send(Member member, String verifyUrl) {
        // 비밀번호는 여기서도 절대 로그에 쓰지 않는다
        log.info("[DEV] 이메일 인증 링크 (email={}): {}", member.getEmail(), verifyUrl);
    }
}
