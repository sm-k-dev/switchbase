package com.switchbase.member;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 로그인 사용자. isEnabled()는 항상 true다.
 * 미인증 차단은 DaoAuthenticationProvider에서 비밀번호 확인 뒤에 한다(SecurityConfig 참고).
 * 그래야 존재하는 이메일이라는 사실이 비밀번호를 모르는 사람에게 드러나지 않는다.
 */
public class MemberUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String nickname;
    private final Role role;
    private final boolean emailVerified;

    public MemberUserDetails(Member member) {
        this.id = member.getId();
        this.email = member.getEmail();
        this.passwordHash = member.getPasswordHash();
        this.nickname = member.getNickname();
        this.role = member.getRole();
        this.emailVerified = member.isEmailVerified();
    }

    @Override
    public String getUsername() { return email; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public Long getId() { return id; }

    public boolean isAdmin() { return role == Role.ADMIN; }

    public String getNickname() { return nickname; }

    public boolean isEmailVerified() { return emailVerified; }

    @Override
    public boolean isEnabled() { return true; }
}
