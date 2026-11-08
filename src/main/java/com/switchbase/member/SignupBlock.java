package com.switchbase.member;

import jakarta.persistence.*;

import java.time.Instant;

/** 탈퇴한 이메일의 해시와 재가입 허용 시각 (Q15, Q19). 원문 이메일은 저장하지 않는다. */
@Entity
@Table(name = "signup_block")
public class SignupBlock {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SignupBlock() {}

    SignupBlock(String emailHash, Instant expiresAt) {
        this.emailHash = emailHash;
        this.expiresAt = expiresAt;
    }

    void extendTo(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getExpiresAt() { return expiresAt; }
}
