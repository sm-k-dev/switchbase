package com.switchbase.member;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class MemberService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final int MAX_PENDING_TOKENS = 5;

    static final String UK_EMAIL = "uk_member_email";
    static final String UK_NICKNAME = "uk_member_nickname";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;
    private final EmailVerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final VerificationNotifier notifier;
    private final String baseUrl;

    public MemberService(MemberRepository members,
                         EmailVerificationTokenRepository tokens,
                         PasswordEncoder passwordEncoder,
                         VerificationNotifier notifier,
                         @Value("${switchbase.base-url}") String baseUrl,
                         SignupBlockRepository blocks) {
        this.members = members;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.baseUrl = baseUrl;
        this.blocks = blocks;
    }

    private final SignupBlockRepository blocks;

    /**
     * 가입. 중복 검사는 이 메서드 한 곳에서 한다(계정 존재 노출 정책을 바꿀 때 여기만 고치면 된다).
     */
    @Transactional
    public Member signup(SignupForm form) {
        String email = Member.normalizeEmail(form.getEmail());
        // 탈퇴 후 보관 기간 안의 이메일은 다시 가입할 수 없다(Q15)
        if (blocks.findById(sha256Hex(email))
                .filter(b -> b.getExpiresAt().isAfter(java.time.Instant.now())).isPresent()) {
            throw new SignupBlockedException();
        }
        String nickname = form.getNickname().trim();

        if (members.existsByEmail(email)) {
            throw new DuplicateMemberException("email", "이미 사용 중인 이메일입니다");
        }
        if (members.existsByNickname(nickname)) {
            throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다");
        }

        // 확인과 저장 사이에 같은 값이 들어올 수 있으므로(동시 가입), 유니크 제약 위반도 같은 중복 안내로 바꾼다 (사전 리뷰 M1)
        Member member;
        try {
            member = members.saveAndFlush(new Member(
                    email, passwordEncoder.encode(form.getPassword()), nickname, Role.USER, false));
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains(UK_EMAIL)) {
                throw new DuplicateMemberException("email", "이미 사용 중인 이메일입니다");
            }
            if (cause.contains(UK_NICKNAME)) {
                throw new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다");
            }
            throw e;
        }
        issueToken(member);
        return member;
    }

    /** 인증 링크 처리. 성공하면 true. 만료·재사용·알 수 없는 토큰은 모두 false. */
    @Transactional
    public boolean verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        Optional<EmailVerificationToken> found = tokens.findByTokenHash(sha256Hex(rawToken));
        if (found.isEmpty()) {
            return false;
        }
        EmailVerificationToken token = found.get();
        Instant now = Instant.now();
        if (!token.isUsableAt(now)) {
            return false;
        }
        token.markUsed(now);
        token.getMember().verifyEmail();
        return true;
    }

    /**
     * 미인증 회원에게만 새 토큰을 보낸다. 없는 이메일이어도 호출자에게는 같은 응답을 준다.
     * 미사용·미만료 토큰이 이미 MAX_PENDING_TOKENS개면 발급·로그를 모두 건너뛴다 (사전 리뷰 R1).
     */
    @Transactional
    public void resendVerification(String email) {
        members.findByEmail(Member.normalizeEmail(email))
                .filter(member -> !member.isEmailVerified())
                .filter(member -> tokens.countByMemberIdAndUsedAtIsNullAndExpiresAtAfter(
                        member.getId(), Instant.now()) < MAX_PENDING_TOKENS)
                .ifPresent(this::issueToken);
    }

    private void issueToken(Member member) {
        String raw = newRawToken();
        tokens.save(new EmailVerificationToken(member, sha256Hex(raw), Instant.now().plus(TOKEN_TTL)));
        notifier.send(member, baseUrl + "/verify?token=" + raw);
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
