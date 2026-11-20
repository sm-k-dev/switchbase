package com.switchbase.member;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupBlockHasherTest {

    @Test
    void sameEmailAndSecretGiveSameHash() {
        SignupBlockHasher hasher = new SignupBlockHasher("secret-a");
        assertThat(hasher.hash("user@example.com")).isEqualTo(hasher.hash("user@example.com"));
    }

    @Test
    void differentSecretGivesDifferentHash() {
        String email = "user@example.com";
        assertThat(new SignupBlockHasher("secret-a").hash(email))
                .isNotEqualTo(new SignupBlockHasher("secret-b").hash(email));
    }

    @Test
    void hashIsNotPlainSha256OfEmail() {
        String email = "user@example.com";
        assertThat(new SignupBlockHasher("secret-a").hash(email))
                .isNotEqualTo(MemberService.sha256Hex(email));
    }

    @Test
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> new SignupBlockHasher(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIGNUP_BLOCK_SECRET");
        assertThatThrownBy(() -> new SignupBlockHasher("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
