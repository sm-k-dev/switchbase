package com.switchbase.member;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository members;

    public MemberUserDetailsService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return members.findByEmail(Member.normalizeEmail(username))
                .map(MemberUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다"));
    }
}
