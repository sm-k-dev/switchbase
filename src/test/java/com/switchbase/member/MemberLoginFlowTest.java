package com.switchbase.member;

import com.switchbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 실제 필터체인으로 가입 → 인증 → 로그인 → 권한 → 로그아웃 흐름을 확인한다. @WithMockUser는 쓰지 않는다.
 */
@Import({TestcontainersConfiguration.class, CapturingNotifierConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class MemberLoginFlowTest {

    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";
    static final String PASSWORD = "password123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CapturingNotifierConfig.Capturing capturing;

    // 1. 404 회귀 방지
    @Test
    void loginPageIsServed() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("member/login"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    // R5. 앞뒤 공백은 검증 전에 제거한다
    @Test
    void nicknameIsTrimmedBeforeValidation() throws Exception {
        signup(uniqueEmail(), " a ", PASSWORD, PASSWORD)
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "nickname"));

        String email = uniqueEmail();
        String nickname = uniqueNickname();
        signup(email, "  " + nickname + "  ", PASSWORD, PASSWORD)
                .andExpect(view().name("member/signup-done"));
        assertThat(members.findByEmail(email).orElseThrow().getNickname()).isEqualTo(nickname);
    }

    // 2. 미인증 차단 → 인증 → 로그인 성공
    @Test
    void unverifiedMemberIsBlockedUntilEmailIsVerified() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();

        signup(email, nickname, PASSWORD, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(view().name("member/signup-done"));

        login(email, PASSWORD).andExpect(redirectedUrl("/login?unverified"));

        verify(capturing.lastToken(email))
                .andExpect(status().isOk())
                .andExpect(view().name("member/verify-result"))
                .andExpect(model().attribute("success", true));

        login(email, PASSWORD)
                .andExpect(redirectedUrl("/switches"))
                .andExpect(authenticated().withUsername(email));
    }

    // 3. 미인증 여부 노출 방지 + 비밀번호 바이트 한계
    @Test
    void unverifiedStatusIsRevealedOnlyToPasswordHolder() throws Exception {
        String email = uniqueEmail();
        signup(email, uniqueNickname(), PASSWORD, PASSWORD);

        // 미인증 + 틀린 비밀번호 → 미인증 여부를 드러내지 않고 일반 오류
        login(email, "wrong-password").andExpect(redirectedUrl("/login?error"));
        // 미인증 + 맞는 비밀번호 → 미인증 안내
        login(email, PASSWORD).andExpect(redirectedUrl("/login?unverified"));
        // 존재하지 않는 이메일 → 일반 오류
        login(uniqueEmail(), PASSWORD).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void wrongPasswordForVerifiedMemberShowsError() throws Exception {
        String email = uniqueEmail();
        signupAndVerify(email, uniqueNickname(), PASSWORD);

        login(email, "wrong-password").andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void passwordOver72BytesIsRejectedAndExactly72BytesIsAccepted() throws Exception {
        String longKorean = "가".repeat(25); // 75 bytes
        signup(uniqueEmail(), uniqueNickname(), longKorean, longKorean)
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "password"));

        String exactKorean = "가".repeat(24); // 72 bytes
        signup(uniqueEmail(), uniqueNickname(), exactKorean, exactKorean)
                .andExpect(view().name("member/signup-done"));
    }

    // 4. 토큰 재사용·만료·알 수 없는 토큰 → 실패 화면
    @Test
    void tokenIsSingleUse() throws Exception {
        String email = uniqueEmail();
        signup(email, uniqueNickname(), PASSWORD, PASSWORD);
        String token = capturing.lastToken(email);

        verify(token).andExpect(model().attribute("success", true));
        verify(token)
                .andExpect(view().name("member/verify-result"))
                .andExpect(model().attribute("success", false));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        signup(email, uniqueNickname(), PASSWORD, PASSWORD);
        jdbc.update("UPDATE email_verification_token SET expires_at = now() - interval '1 hour' "
                + "WHERE member_id = (SELECT id FROM member WHERE email = ?)", email);

        verify(capturing.lastToken(email))
                .andExpect(model().attribute("success", false));
    }

    @Test
    void unknownOrMissingTokenIsRejected() throws Exception {
        mvc.perform(get("/verify").param("token", "no-such-token"))
                .andExpect(model().attribute("success", false));
        mvc.perform(get("/verify"))
                .andExpect(view().name("member/verify-result"))
                .andExpect(model().attribute("success", false));
    }

    // 5. 입력 검증 오류
    @Test
    void duplicateEmailShowsFieldError() throws Exception {
        String email = uniqueEmail();
        signup(email, uniqueNickname(), PASSWORD, PASSWORD);

        signup(email, uniqueNickname(), PASSWORD, PASSWORD)
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "email"));
    }

    @Test
    void duplicateNicknameShowsFieldError() throws Exception {
        String nickname = uniqueNickname();
        signup(uniqueEmail(), nickname, PASSWORD, PASSWORD);

        signup(uniqueEmail(), nickname, PASSWORD, PASSWORD)
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "nickname"));
    }

    @Test
    void shortPasswordShowsFieldError() throws Exception {
        signup(uniqueEmail(), uniqueNickname(), "short77", "short77")
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "password"));
    }

    @Test
    void mismatchedPasswordConfirmShowsFieldError() throws Exception {
        signup(uniqueEmail(), uniqueNickname(), PASSWORD, "different123")
                .andExpect(view().name("member/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "passwordConfirm"));
    }

    // 6. 권한
    @Test
    void seededAdminCanOpenAdminPage() throws Exception {
        MockHttpSession session = loginSession(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(get("/admin/switches/new").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void userIsForbiddenFromAdminPage() throws Exception {
        String email = uniqueEmail();
        signupAndVerify(email, uniqueNickname(), PASSWORD);
        MockHttpSession session = loginSession(email, PASSWORD);

        mvc.perform(get("/admin/switches/new").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsSentToLoginFromAdminPage() throws Exception {
        mvc.perform(get("/admin/switches/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // 7. 관리자 초기화: 재기동해도 기존 관리자의 비밀번호·역할·닉네임은 바뀌지 않는다
    @Test
    void adminInitializerDoesNotOverwriteExistingAdmin() throws Exception {
        String hashBefore = jdbc.queryForObject(
                "SELECT password_hash FROM member WHERE email = ?", String.class, ADMIN_EMAIL);

        AdminAccountInitializer rerun = new AdminAccountInitializer(
                members, passwordEncoder,
                new AdminProperties(ADMIN_EMAIL, "someone-else", "OtherPassw0rd!"));
        rerun.run(new DefaultApplicationArguments());

        String hashAfter = jdbc.queryForObject(
                "SELECT password_hash FROM member WHERE email = ?", String.class, ADMIN_EMAIL);
        String role = jdbc.queryForObject(
                "SELECT role FROM member WHERE email = ?", String.class, ADMIN_EMAIL);
        String nickname = jdbc.queryForObject(
                "SELECT nickname FROM member WHERE email = ?", String.class, ADMIN_EMAIL);

        assertThat(hashAfter).isEqualTo(hashBefore);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, hashAfter)).isTrue();
        assertThat(role).isEqualTo("ADMIN");
        assertThat(nickname).isEqualTo("admin");
    }

    // 8. 로그아웃 후 관리자 페이지 접근 불가
    @Test
    void logoutEndsAdminAccess() throws Exception {
        MockHttpSession session = loginSession(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/admin/switches/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---- helpers ----

    private ResultActions signup(String email, String nickname, String password, String passwordConfirm)
            throws Exception {
        return mvc.perform(post("/signup")
                .param("email", email)
                .param("nickname", nickname)
                .param("password", password)
                .param("passwordConfirm", passwordConfirm)
                .with(csrf()));
    }

    private void signupAndVerify(String email, String nickname, String password) throws Exception {
        signup(email, nickname, password, password);
        verify(capturing.lastToken(email)).andExpect(model().attribute("success", true));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(get("/verify").param("token", token));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/login")
                .param("email", email)
                .param("password", password)
                .with(csrf()));
    }

    /** 로그인에 성공하고 그 세션을 돌려준다. */
    private MockHttpSession loginSession(String email, String password) throws Exception {
        MvcResult result = login(email, password)
                .andExpect(authenticated().withUsername(email))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String uniqueNickname() {
        return "tester" + UUID.randomUUID().toString().substring(0, 6);
    }
}
