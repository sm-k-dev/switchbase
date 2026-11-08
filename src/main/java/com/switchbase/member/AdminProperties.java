package com.switchbase.member;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 첫 관리자 계정 설정. password가 비어 있으면 관리자를 만들지 않는다. */
@ConfigurationProperties("switchbase.admin")
public record AdminProperties(String email, String nickname, String password) {
}
