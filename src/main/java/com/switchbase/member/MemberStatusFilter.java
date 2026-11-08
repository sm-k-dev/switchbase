package com.switchbase.member;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 인증된 요청마다 회원 상태를 DB에서 다시 확인한다 (review-collection-report, 리뷰 R1).
 * 탈퇴(익명화)된 회원의 기존 세션이면 세션을 무효화하고 로그인 화면으로 보낸다.
 * 빈으로 등록하지 않고 SecurityConfig에서 체인에 직접 넣는다(서블릿 중복 등록 방지).
 */
public class MemberStatusFilter extends OncePerRequestFilter {

    private final MemberRepository members;

    public MemberStatusFilter(MemberRepository members) {
        this.members = members;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MemberUserDetails user) {
            boolean active = members.findById(user.getId()).map(m -> !m.isWithdrawn()).orElse(false);
            if (!active) {
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
