package com.switchbase.member;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 관리자 설정값 검증(사전 리뷰 R6). 잘못된 값이면 기동을 막지 않고 건너뛴다. */
class AdminAccountInitializerValidationTest {

    static final String PASSWORD = "AdminPassw0rd!";

    @Test
    void invalidNicknameSkipsCreation() throws Exception {
        assertSkipped(new AdminProperties("ok-admin@example.com", "a", PASSWORD));
        assertSkipped(new AdminProperties("ok-admin@example.com", "a".repeat(21), PASSWORD));
        assertSkipped(new AdminProperties("ok-admin@example.com", "   ", PASSWORD));
        assertSkipped(new AdminProperties("ok-admin@example.com", null, PASSWORD));
    }

    @Test
    void invalidEmailSkipsCreation() throws Exception {
        assertSkipped(new AdminProperties("not-an-email", "admin", PASSWORD));
        assertSkipped(new AdminProperties(null, "admin", PASSWORD));
        assertSkipped(new AdminProperties("a@b", "admin", PASSWORD));
    }

    @Test
    void validSettingsCreateAdmin() throws Exception {
        MemberRepository members = mock(MemberRepository.class);
        new AdminAccountInitializer(members, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                new AdminProperties("ok-admin@example.com", "admin", PASSWORD))
                .run(new DefaultApplicationArguments());

        verify(members).save(any(Member.class));
    }

    private static void assertSkipped(AdminProperties props) throws Exception {
        MemberRepository members = mock(MemberRepository.class);
        new AdminAccountInitializer(members, PasswordEncoderFactories.createDelegatingPasswordEncoder(), props)
                .run(new DefaultApplicationArguments());

        verify(members, never()).save(any(Member.class));
    }
}
