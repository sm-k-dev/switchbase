package com.switchbase.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 폼. 설계서는 record였지만 Thymeleaf th:field가 record 접근자를 읽지 못해 일반 클래스로 둔다.
 */
public class SignupForm {

    @NotBlank(message = "올바른 이메일 주소를 입력해 주세요.")
    @Email(message = "올바른 이메일 주소를 입력해 주세요.")
    @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
    private String email;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자로 입력해 주세요.")
    private String nickname;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 해요.")
    @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 길어요. 한글은 약 24자, 영문·숫자는 72자까지 가능합니다.")
    private String password;

    @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.")
    private String passwordConfirm;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    /** 앞뒤 공백을 먼저 없앤 뒤 검증한다. " a"가 1글자 닉네임으로 통과하지 않도록 (사전 리뷰 R5). */
    public void setNickname(String nickname) { this.nickname = nickname == null ? null : nickname.trim(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
