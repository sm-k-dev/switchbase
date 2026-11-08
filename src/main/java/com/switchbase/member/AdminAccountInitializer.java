package com.switchbase.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 기동 시 첫 관리자 계정을 만든다. 이미 있으면 아무것도 바꾸지 않는다(비밀번호·역할 덮어쓰기 금지).
 */
@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties props;

    public AdminAccountInitializer(MemberRepository members, PasswordEncoder passwordEncoder, AdminProperties props) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = props.password();
        if (password == null || password.isEmpty()) {
            log.warn("관리자 계정을 만들지 않았습니다. ADMIN_PASSWORD 환경 변수를 설정하면 기동 때 관리자가 만들어집니다.");
            return;
        }
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            log.warn("ADMIN_PASSWORD는 8자 이상 72바이트 이하여야 합니다. 관리자 계정을 만들지 않았습니다.");
            return;
        }

        String email = Member.normalizeEmail(props.email());
        if (email == null || email.length() > 255 || !EMAIL_SHAPE.matcher(email).matches()) {
            log.warn("ADMIN_EMAIL 형식이 올바르지 않아 관리자 계정을 만들지 않았습니다.");
            return;
        }
        String nickname = props.nickname() == null ? "" : props.nickname().trim();
        if (nickname.length() < 2 || nickname.length() > 20) {
            log.warn("ADMIN_NICKNAME은 2~20자여야 합니다. 관리자 계정을 만들지 않았습니다.");
            return;
        }
        if (members.existsByEmail(email)) {
            return; // 이미 있으면 그대로 둔다
        }
        if (members.existsByNickname(nickname)) {
            log.warn("ADMIN_NICKNAME '{}'은 이미 다른 회원이 쓰고 있어 관리자 계정을 만들지 않았습니다.", nickname);
            return;
        }

        members.save(new Member(email, passwordEncoder.encode(password), nickname, Role.ADMIN, true));
        log.info("관리자 계정을 만들었습니다 (email={})", email);
    }
}
