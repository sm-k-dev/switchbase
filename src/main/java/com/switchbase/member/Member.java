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

    /** 탈퇴 시 NULL로 익명화된다(review-collection-report Q19). */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "collection_public", nullable = false)
    private boolean collectionPublic;

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

    /**
     * 탈퇴 익명화: 이메일·비밀번호 해시를 지우고 닉네임을 유일한 "탈퇴회원<id>"로 바꾼다.
     * 행 자체는 남긴다(편집 기록과 평가의 작성자 참조를 유지하려고).
     */
    public void withdraw(Instant now) {
        this.email = null;
        this.passwordHash = null;
        this.nickname = "탈퇴회원" + this.id;
        this.collectionPublic = false;
        this.deletedAt = now;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    public Instant getDeletedAt() { return deletedAt; }

    public boolean isCollectionPublic() { return collectionPublic; }

    public void changeCollectionPublic(boolean collectionPublic) {
        this.collectionPublic = collectionPublic;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
}
