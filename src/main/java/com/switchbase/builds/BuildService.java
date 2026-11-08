package com.switchbase.builds;

import com.switchbase.member.Member;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class BuildService {

    public static final int MAX_BUILDS_PER_MEMBER = 20;

    private final KeyboardBuildRepository builds;

    public BuildService(KeyboardBuildRepository builds) {
        this.builds = builds;
    }

    /** 숨김 포함 개수(회원당 한도 검사와 같은 기준). */
    public long countBuildsOf(Long memberId) {
        return builds.countByMemberId(memberId);
    }

    public Page<KeyboardBuild> visibleBuilds(Pageable pageable) {
        return builds.findVisible(com.switchbase.common.Retention.cutoff(java.time.Instant.now()), pageable);
    }

    /** 숨김 빌드는 작성자 외에게 없는 것처럼 404다. 상세·수정 화면이 같은 규칙을 쓴다. */
    public KeyboardBuild view(Long id, Long viewerId) {
        KeyboardBuild build = load(id);
        requireVisibleTo(build, viewerId);
        return build;
    }

    private static void requireVisibleTo(KeyboardBuild build, Long viewerId) {
        if (build.isHidden() && !Objects.equals(viewerId, build.getMember().getId())) {
            throw new NoSuchElementException("빌드가 없습니다: " + build.getId());
        }
    }

    public KeyboardBuild load(Long id) {
        return builds.findWithMember(id)
                .orElseThrow(() -> new NoSuchElementException("빌드가 없습니다: " + id));
    }

    @Transactional
    public KeyboardBuild create(Long memberId, BuildInput raw) {
        BuildInput in = validated(raw);
        Member member = builds.lockMember(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));
        if (builds.countByMemberId(memberId) >= MAX_BUILDS_PER_MEMBER) {
            throw new BuildLimitExceededException();
        }
        return builds.save(new KeyboardBuild(member, in));
    }

    /**
     * 수정. 공개 빌드는 누구나 수정할 수 있다(Q1). 숨김 빌드는 작성자만 수정할 수 있고, 그 외에는 404다(R2).
     * 옛 version이면 충돌. 이력은 남기지 않는다(Q8).
     */
    @Transactional
    public KeyboardBuild update(Long id, Long viewerId, Long expectedVersion, BuildInput raw) {
        BuildInput in = validated(raw);
        KeyboardBuild build = lockForUpdate(id);
        requireVisibleTo(build, viewerId);
        if (expectedVersion == null || build.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다: " + id);
        }
        build.apply(in);
        return build;
    }

    /** 누구나 숨기거나 숨김을 풀 수 있다(Q1, Q4). 데이터는 지우지 않는다. */
    @Transactional
    public KeyboardBuild setHidden(Long id, boolean hidden) {
        KeyboardBuild build = lockForUpdate(id);
        build.changeHidden(hidden);
        return build;
    }

    /**
     * 숨김 해제. 숨김 빌드는 작성자만 풀 수 있고, 다른 회원(관리자 포함)에게는 404다(사용자 확정).
     * 공개 빌드에 대한 해제 요청은 상태를 바꾸지 않는다.
     */
    @Transactional
    public KeyboardBuild unhide(Long id, Long viewerId) {
        KeyboardBuild build = lockForUpdate(id);
        requireVisibleTo(build, viewerId);
        if (build.isHidden()) {
            build.changeHidden(false);
        }
        return build;
    }

    private KeyboardBuild lockForUpdate(Long id) {
        return builds.findForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("빌드가 없습니다: " + id));
    }

    /** 빈 이름·메모는 NULL로, 고정 선택지는 범위 안인지 확인한다. */
    BuildInput validated(BuildInput raw) {
        for (BuildPart part : BuildPart.values()) {
            if (!part.accepts(choiceOf(raw, part))) {
                throw new BuildChoiceException(part);
            }
        }
        return new BuildInput(
                blankToNull(raw.name()),
                blankToNull(raw.housingMaterial()),
                blankToNull(raw.plateMaterial()),
                blankToNull(raw.mountType()),
                blankToNull(raw.keycapMaterial()),
                blankToNull(raw.memo()));
    }

    private static String choiceOf(BuildInput in, BuildPart part) {
        return switch (part) {
            case HOUSING -> blankToNull(in.housingMaterial());
            case PLATE -> blankToNull(in.plateMaterial());
            case MOUNT -> blankToNull(in.mountType());
            case KEYCAP -> blankToNull(in.keycapMaterial());
        };
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
