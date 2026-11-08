package com.switchbase.common;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 매일 한 번 04:30에 보관 기간이 지난 행을 지운다(Q17). 주기는 이 한 곳에서만 바꾼다. */
@Component
@EnableScheduling
public class RetentionScheduler {

    private final RetentionService retention;

    public RetentionScheduler(RetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "0 30 4 * * *")
    void purgeDaily() {
        retention.purge(Instant.now());
    }
}
