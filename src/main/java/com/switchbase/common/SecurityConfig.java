package com.switchbase.common;

import com.switchbase.member.MemberUserDetails;
import com.switchbase.member.MemberUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            MemberUserDetailsService userDetailsService,
                                            PasswordEncoder passwordEncoder) throws Exception {
        http
                .authenticationProvider(memberAuthenticationProvider(userDetailsService, passwordEncoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 위키: 수정·되돌리기는 로그인 회원만. 상세·이력·비교·최근 변경은 누구나 본다 (wiki-design §5.6)
                        .requestMatchers(HttpMethod.GET, "/switches/*/edit", "/switches/*/revisions/*/revert").authenticated()
                        .requestMatchers(HttpMethod.POST, "/switches/*", "/switches/*/revisions/*/revert").authenticated()
                        // 빌드: 목록·상세는 누구나, 등록·수정·숨김은 로그인 회원 (build-design §4)
                        .requestMatchers(HttpMethod.GET, "/builds/new", "/builds/*/edit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/builds", "/builds/*", "/builds/*/hide", "/builds/*/unhide").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")              // 우리가 만든 로그인 화면
                        .usernameParameter("email")       // 아이디 칸 이름이 email
                        .defaultSuccessUrl("/switches")
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(request.getContextPath()
                                        + (exception instanceof DisabledException ? "/login?unverified" : "/login?error"))))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // 토큰이 든 /verify URL이 외부 사이트로 새지 않도록 Referer를 보내지 않는다 (사전 리뷰 R3)
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    // 저장된 값 앞에 {bcrypt}처럼 방식이 붙는다. 나중에 방식을 바꿔도 옛 비밀번호가 그대로 맞는다.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 미인증 차단은 비밀번호 확인 뒤(additionalAuthenticationChecks)에서 한다.
     * UserDetails.isEnabled()로 막으면 비밀번호를 몰라도 "가입돼 있고 미인증"임이 드러난다.
     */
    private static DaoAuthenticationProvider memberAuthenticationProvider(MemberUserDetailsService userDetailsService,
                                                                          PasswordEncoder passwordEncoder) {
        // Security 7.1: 생성자는 UserDetailsService를 받고, 인코더는 setter로 넣는다.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService) {
            @Override
            protected void additionalAuthenticationChecks(UserDetails user,
                                                          UsernamePasswordAuthenticationToken authentication) {
                super.additionalAuthenticationChecks(user, authentication); // 비밀번호가 틀리면 여기서 BadCredentials
                if (user instanceof MemberUserDetails member && !member.isEmailVerified()) {
                    throw new DisabledException("이메일 인증이 필요합니다");
                }
            }
        };
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
