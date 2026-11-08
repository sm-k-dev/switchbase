package com.switchbase.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** 회원의 아직 쓰지 않았고 만료되지 않은 토큰 수 (재발송 상한 검사용, 사전 리뷰 R1). */
    long countByMemberIdAndUsedAtIsNullAndExpiresAtAfter(Long memberId, Instant now);
}
