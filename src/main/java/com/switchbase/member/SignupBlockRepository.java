package com.switchbase.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SignupBlockRepository extends JpaRepository<SignupBlock, String> {

    @Modifying
    @Query("delete from SignupBlock b where b.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
