package com.switchbase.member;

import com.switchbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, CapturingNotifierConfig.class})
@SpringBootTest
class MemberServiceTest {

    @Autowired MemberService memberService;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingNotifierConfig.Capturing capturing;

    @Test
    void verificationTokenIsStoredOnlyAsHashWithTwentyFourHourExpiry() {
        String email = "svc-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        SignupForm form = new SignupForm();
        form.setEmail(email);
        form.setNickname("svc" + UUID.randomUUID().toString().substring(0, 8));
        form.setPassword("password123");
        form.setPasswordConfirm("password123");

        Instant before = Instant.now();
        memberService.signup(form);

        String raw = capturing.lastToken(email);
        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM email_verification_token t JOIN member m ON m.id = t.member_id "
                        + "WHERE m.email = ?", String.class, email);
        Timestamp expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM email_verification_token t JOIN member m ON m.id = t.member_id "
                        + "WHERE m.email = ?", Timestamp.class, email);

        // 원문은 DB에 없고 SHA-256 해시만 있다
        assertThat(storedHash).isEqualTo(MemberService.sha256Hex(raw));
        assertThat(storedHash).isNotEqualTo(raw);

        // 만료 시각은 발급 시점 + 24시간 (여유 있게 검사)
        Instant expires = expiresAt.toInstant();
        assertThat(expires).isBetween(before.plus(Duration.ofHours(23)), before.plus(Duration.ofHours(25)));
    }
}
