package com.switchbase.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")              // 우리가 만든 로그인 화면
                        .usernameParameter("email")       // 아이디 칸 이름이 email
                        .defaultSuccessUrl("/switches"))
                .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    // 저장된 값 앞에 {bcrypt}처럼 방식이 붙는다. 나중에 방식을 바꿔도 옛 비밀번호가 그대로 맞는다.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
