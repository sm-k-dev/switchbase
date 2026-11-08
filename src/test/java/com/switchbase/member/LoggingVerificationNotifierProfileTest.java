package com.switchbase.member;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영(prod)에서는 로그 출력 Notifier가 없어야 한다(사전 리뷰 M2). DB 없이 컨텍스트만 확인한다. */
class LoggingVerificationNotifierProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LoggingVerificationNotifier.class);

    @Test
    void enabledOutsideProd() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(VerificationNotifier.class));
    }

    @Test
    void disabledUnderProdProfile() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(VerificationNotifier.class));
    }
}
