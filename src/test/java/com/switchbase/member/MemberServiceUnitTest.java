package com.switchbase.member;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB 없이 저장소를 흉내 내는 단위 테스트. 동시 가입 경합(M1)과 재발송 상한(R1)을 확인한다.
 */
class MemberServiceUnitTest {

    MemberRepository members = mock(MemberRepository.class);
    EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
    VerificationNotifier notifier = mock(VerificationNotifier.class);
    SignupBlockRepository blocks = mock(SignupBlockRepository.class);
    MemberService service = new MemberService(members, tokens,
            PasswordEncoderFactories.createDelegatingPasswordEncoder(), notifier, "http://localhost:8090", blocks);

    // M1. 존재 확인 뒤 저장 전에 다른 요청이 같은 값을 넣은 경우
    @Test
    void uniqueViolationOnEmailBecomesDuplicateEmailError() {
        whenNoDuplicatesFound();
        when(members.saveAndFlush(any(Member.class))).thenThrow(uniqueViolation("uk_member_email"));

        assertThatThrownBy(() -> service.signup(form("race@example.com", "racer01")))
                .isInstanceOfSatisfying(DuplicateMemberException.class,
                        e -> assertThat(e.getField()).isEqualTo("email"));
    }

    @Test
    void uniqueViolationOnNicknameBecomesDuplicateNicknameError() {
        whenNoDuplicatesFound();
        when(members.saveAndFlush(any(Member.class))).thenThrow(uniqueViolation("uk_member_nickname"));

        assertThatThrownBy(() -> service.signup(form("race2@example.com", "racer02")))
                .isInstanceOfSatisfying(DuplicateMemberException.class,
                        e -> assertThat(e.getField()).isEqualTo("nickname"));
    }

    @Test
    void otherIntegrityViolationIsNotHiddenAsDuplicate() {
        whenNoDuplicatesFound();
        when(members.saveAndFlush(any(Member.class))).thenThrow(uniqueViolation("ck_member_role"));

        assertThatThrownBy(() -> service.signup(form("race3@example.com", "racer03")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // R1. 미사용·미만료 토큰이 5개면 재발송해도 새 토큰도, 발송도 없다
    @Test
    void resendIsSkippedWhenFivePendingTokensExist() {
        Member unverified = new Member("pending@example.com", "{bcrypt}x", "pending01", Role.USER, false);
        when(members.findByEmail("pending@example.com")).thenReturn(Optional.of(unverified));
        when(tokens.countByMemberIdAndUsedAtIsNullAndExpiresAtAfter(any(), any())).thenReturn(5L);

        service.resendVerification("pending@example.com");

        verify(tokens, never()).save(any());
        verify(notifier, never()).send(any(), anyString());
    }

    @Test
    void resendIssuesTokenWhenFewerThanFivePendingTokens() {
        Member unverified = new Member("few@example.com", "{bcrypt}x", "few0001", Role.USER, false);
        when(members.findByEmail("few@example.com")).thenReturn(Optional.of(unverified));
        when(tokens.countByMemberIdAndUsedAtIsNullAndExpiresAtAfter(any(), any())).thenReturn(4L);

        service.resendVerification("few@example.com");

        verify(tokens).save(any());
        verify(notifier).send(any(), anyString());
    }

    private void whenNoDuplicatesFound() {
        when(members.existsByEmail(anyString())).thenReturn(false);
        when(members.existsByNickname(anyString())).thenReturn(false);
    }

    private static DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("constraint violated",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    private static SignupForm form(String email, String nickname) {
        SignupForm form = new SignupForm();
        form.setEmail(email);
        form.setNickname(nickname);
        form.setPassword("password123");
        form.setPasswordConfirm("password123");
        return form;
    }
}
