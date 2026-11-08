package com.switchbase.member;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Member() {}

    public Member(String email, String passwordHash, String nickname, Role role, boolean emailVerified) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = role;
        this.emailVerified = emailVerified;
        this.createdAt = Instant.now();
    }

    /** 이메일 정규화: 앞뒤 공백 제거 + 소문자. 저장과 조회 모두 이 규칙을 쓴다. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
}
