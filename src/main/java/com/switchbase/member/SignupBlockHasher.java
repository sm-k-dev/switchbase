package com.switchbase.member;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 탈퇴 회원 재가입 차단용 이메일 해시. 서버 비밀값으로 만든 HMAC-SHA256을 쓴다.
 * 솔트 없는 SHA-256이면 DB가 유출됐을 때 후보 이메일을 대입해 탈퇴 여부를 알아낼 수 있으므로(리뷰 권고),
 * 비밀값 없이는 같은 해시를 만들 수 없게 한다. 비밀값(SIGNUP_BLOCK_SECRET)이 없으면 기동하지 않는다.
 */
@Component
public class SignupBlockHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SignupBlockHasher(@Value("${switchbase.signup-block.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "SIGNUP_BLOCK_SECRET 환경 변수가 없습니다. 재가입 차단 해시에 필요합니다.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 정규화된 이메일(소문자)의 HMAC-SHA256 hex. */
    public String hash(String normalizedEmail) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다", e);
        }
    }
}
