package com.switchbase.member;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 인증 링크 캡처. 실제 발송(로그 출력) 대신 메모리에 보관해 테스트가 링크를 꺼내 쓰게 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CapturingNotifierConfig {

    @Bean
    @Primary
    Capturing capturingVerificationNotifier() {
        return new Capturing();
    }

    public static class Capturing implements VerificationNotifier {

        private final Map<String, String> urlByEmail = new ConcurrentHashMap<>();

        @Override
        public void send(Member member, String verifyUrl) {
            urlByEmail.put(member.getEmail(), verifyUrl);
        }

        public String lastUrl(String email) {
            return urlByEmail.get(Member.normalizeEmail(email));
        }

        /** 마지막으로 발송된 인증 링크의 토큰 원문. */
        public String lastToken(String email) {
            String url = lastUrl(email);
            if (url == null) {
                throw new IllegalStateException("인증 링크가 발송되지 않았습니다: " + email);
            }
            return url.substring(url.indexOf("token=") + "token=".length());
        }
    }
}
