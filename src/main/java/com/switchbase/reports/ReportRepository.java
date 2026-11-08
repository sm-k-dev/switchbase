package com.switchbase.reports;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    Page<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    /** 신고자 탈퇴: 연결만 끊는다(§6-4 구현 영향 4). */
    @Modifying
    @Query("update Report r set r.reporter = null where r.reporter.id = :mid")
    int disconnectReporter(@Param("mid") Long memberId);
}
