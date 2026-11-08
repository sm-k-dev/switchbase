package com.switchbase.builds;

import com.switchbase.member.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KeyboardBuildRepository extends JpaRepository<KeyboardBuild, Long> {

    /** 상세·목록용. 작성자 닉네임을 같이 읽어 N+1을 막는다. */
    @Query("select b from KeyboardBuild b join fetch b.member where b.id = :id")
    Optional<KeyboardBuild> findWithMember(@Param("id") Long id);

    /** 수정·숨김 직렬화용 행 잠금. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from KeyboardBuild b where b.id = :id")
    Optional<KeyboardBuild> findForUpdate(@Param("id") Long id);

    /** 등록 한도 검사를 회원 단위로 직렬화하기 위해 회원 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockMember(@Param("id") Long id);

    /** 평가 폼의 빌드 목록(본인 것, 숨김 포함). */
    List<KeyboardBuild> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /** 숨김 빌드도 포함해서 센다(Q3). */
    long countByMemberId(Long memberId);

    /** 숨김 제외. 탈퇴 회원의 빌드는 보관 기간(cutoff 이후 탈퇴)까지만 보인다. */
    @Query(value = "select b from KeyboardBuild b join fetch b.member m where b.hidden = false "
            + "and b.deletedAt is null and (m.deletedAt is null or m.deletedAt > :cutoff) "
            + "order by b.createdAt desc, b.id desc",
            countQuery = "select count(b) from KeyboardBuild b join b.member m where b.hidden = false "
            + "and b.deletedAt is null and (m.deletedAt is null or m.deletedAt > :cutoff)")
    Page<KeyboardBuild> findVisible(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
                                    Pageable pageable);
}
