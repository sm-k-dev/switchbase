package com.switchbase.ratings;

import com.switchbase.builds.BuildView;
import com.switchbase.common.Retention;
import com.switchbase.builds.KeyboardBuild;
import com.switchbase.builds.KeyboardBuildRepository;
import com.switchbase.builds.Option;
import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.keyswitch.KeyboardSwitchRepository;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * 체감 평가 (rating-design §4·§5). 권한은 여기서 검사한다.
 * 수정은 작성자만(아니면 403), 삭제는 작성자 또는 관리자(아니면 403).
 */
@Service
@Transactional(readOnly = true)
public class RatingService {

    static final int MAX_TAGS = 10;
    static final int TAG_MAX_LENGTH = 50;

    private final RatingRepository ratings;
    private final ModTagRepository tags;
    private final KeyboardSwitchRepository switches;
    private final KeyboardBuildRepository builds;
    private final MemberRepository members;
    private final ModTagWriter tagWriter;
    private final RatingStatsService stats;

    public RatingService(RatingRepository ratings, ModTagRepository tags, KeyboardSwitchRepository switches,
                         KeyboardBuildRepository builds, MemberRepository members, ModTagWriter tagWriter,
                         RatingStatsService stats) {
        this.ratings = ratings;
        this.tags = tags;
        this.switches = switches;
        this.builds = builds;
        this.members = members;
        this.tagWriter = tagWriter;
        this.stats = stats;
    }

    // ---- 조회 (누구나) ----

    /** 요약은 집계 행에서 읽는다(stats-design §5-1). */
    public RatingSummary summary(Long switchId) {
        requireSwitch(switchId);
        return stats.current(switchId);
    }

    public Page<RatingView> list(Long switchId, Long viewerId, boolean viewerAdmin, Pageable pageable) {
        requireSwitch(switchId);
        Long viewer = viewerId == null ? -1L : viewerId;
        return ratings.findBySwitch(switchId, viewer, Retention.cutoff(Instant.now()), pageable)
                .map(r -> view(r, viewerId, viewerAdmin));
    }

    /** 수정 화면용. 작성자가 아니면 403이다(rating-screens §1, 평가는 공개 데이터). */
    public Rating loadForEdit(Long ratingId, Long viewerId) {
        Rating r = load(ratingId);
        if (!isAuthor(r, viewerId)) {
            throw new RatingAccessDeniedException("작성자만 고칠 수 있어요");
        }
        return r;
    }

    /** 수정 폼 값. 태그를 읽어야 하므로 트랜잭션 안에서 만든다(open-in-view가 꺼져 있다). */
    public RatingForm formForEdit(Long ratingId, Long viewerId) {
        return RatingForm.from(loadForEdit(ratingId, viewerId));
    }

    /** 평가 폼의 빌드 select. 첫 항목은 "빌드 없음"이고 code는 null이다(빈 값으로 전송). */
    public List<Option> buildOptionsFor(Long memberId) {
        List<Option> options = new ArrayList<>();
        options.add(new Option(null, "빌드 없음"));
        for (KeyboardBuild b : builds.findByMemberIdOrderByCreatedAtDesc(memberId)) {
            options.add(new Option(String.valueOf(b.getId()), BuildView.from(b).displayName()));
        }
        return options;
    }

    /** 자동완성. 마지막 쉼표 뒤 토큰을 앞부분 일치로 찾는다(rating-screens §1). */
    public List<String> suggestTags(String modTagsRaw) {
        String raw = modTagsRaw == null ? "" : modTagsRaw;
        String token = raw.substring(raw.lastIndexOf(',') + 1);
        String key = normalize(token).toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return List.of();
        }
        return tags.findTop10ByNormalizedStartingWithOrderByNormalizedAsc(key).stream()
                .map(ModTag::getName)
                .toList();
    }

    // ---- 변경 ----

    /** 등록. 같은 (스위치, 빌드) 조합이 있으면 거부한다(Q2). 빌드는 본인 것만 붙인다. */
    @Transactional
    public Rating create(Long switchId, Long memberId, RatingInput in) {
        validate(in);
        KeyboardSwitch sw = switches.findById(switchId)
                .orElseThrow(() -> new NoSuchElementException("스위치가 없습니다: " + switchId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));
        KeyboardBuild build = ownedBuild(in.buildId(), memberId);
        if (ratings.findFirstByKeyboardSwitchIdAndMemberIdAndBuildIdAndDeletedAtIsNull(
                switchId, memberId, buildIdOf(build)).isPresent()) {
            throw new RatingDuplicateException();
        }
        Rating saved = saveRating(new Rating(sw, member, build, in, resolveTags(in.tags())));
        stats.recompute(switchId);
        return saved;
    }

    /** 수정. 작성자만(아니면 403). 옛 version이면 충돌. 조합이 바뀌면 중복을 다시 본다. */
    @Transactional
    public Rating update(Long ratingId, Long viewerId, Long expectedVersion, RatingInput in) {
        validate(in);
        Rating r = loadForEdit(ratingId, viewerId);
        if (expectedVersion == null || r.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 고쳤습니다: " + ratingId);
        }
        KeyboardBuild build = ownedBuild(in.buildId(), viewerId);
        Long newBuildId = buildIdOf(build);
        Long oldBuildId = r.getBuild() == null ? null : r.getBuild().getId();
        if (!Objects.equals(newBuildId, oldBuildId)) {
            var other = ratings.findFirstByKeyboardSwitchIdAndMemberIdAndBuildIdAndDeletedAtIsNull(
                    r.getKeyboardSwitch().getId(), viewerId, newBuildId);
            if (other.isPresent() && !other.get().getId().equals(r.getId())) {
                throw new RatingDuplicateException();
            }
        }
        r.apply(build, in, resolveTags(in.tags()));
        Rating saved = saveRating(r);
        stats.recompute(saved.getKeyboardSwitch().getId());
        return saved;
    }

    /** 삭제. 작성자 또는 관리자만(아니면 403). 삭제된 평가가 속한 스위치 id를 돌려준다. */
    @Transactional
    public Long delete(Long ratingId, Long viewerId, boolean viewerAdmin) {
        Rating r = load(ratingId);
        if (!viewerAdmin && !isAuthor(r, viewerId)) {
            throw new RatingAccessDeniedException("작성자나 관리자만 삭제할 수 있어요");
        }
        Long switchId = r.getKeyboardSwitch().getId();
        r.softDelete(Instant.now());
        ratings.flush();
        stats.recompute(switchId);
        return switchId;
    }

    // ---- 내부 ----

    private Rating load(Long ratingId) {
        return ratings.findById(ratingId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new NoSuchElementException("평가가 없습니다: " + ratingId));
    }

    /**
     * 숨김 해제. 작성자만 가능하고, 그 외(관리자 포함)에게는 404다(사용자 확정).
     * 숨기지 않은 평가에 대한 요청은 상태를 바꾸지 않는다.
     */
    @Transactional
    public Long unhide(Long ratingId, Long viewerId) {
        Rating r = load(ratingId);
        if (!isAuthor(r, viewerId)) {
            throw new NoSuchElementException("평가가 없습니다: " + ratingId);
        }
        Long switchId = r.getKeyboardSwitch().getId();
        if (r.isHidden()) {
            r.changeHidden(false);
            ratings.flush();
            stats.recompute(switchId);
        }
        return switchId;
    }

    /** 신고 처리 결과로 숨김(관리자 전용 경로에서만 호출). 집계에서 빠진다(Q14). */
    @Transactional
    public void hideByReport(Long ratingId) {
        Rating r = load(ratingId);
        r.changeHidden(true);
        ratings.flush();
        stats.recompute(r.getKeyboardSwitch().getId());
    }

    private void requireSwitch(Long switchId) {
        if (!switches.existsById(switchId)) {
            throw new NoSuchElementException("스위치가 없습니다: " + switchId);
        }
    }

    private static boolean isAuthor(Rating r, Long viewerId) {
        return viewerId != null && viewerId.equals(r.getMember().getId());
    }

    private RatingView view(Rating r, Long viewerId, boolean viewerAdmin) {
        boolean mine = isAuthor(r, viewerId);
        return new RatingView(
                r.getId(),
                r.getMember().getNickname(),
                r.getStarRating().intValue(),
                r.getSoundLevel().intValue(),
                r.getSoundTone().getLabel(),
                r.getFeel().getLabel(),
                r.getFatigueLevel().intValue(),
                lubedLabel(r.getLubed()),
                r.getModTags().stream().map(ModTag::getName).toList(),
                r.getBuild() != null,
                r.getCreatedAt(),
                mine,
                mine || viewerAdmin,
                mine && r.isHidden());
    }

    private static String lubedLabel(Boolean lubed) {
        if (lubed == null) {
            return null;
        }
        return lubed ? "예" : "아니오";
    }

    /** 빌드 id가 없으면 null(빌드 없이 평가). 있으면 본인 빌드여야 한다. */
    private KeyboardBuild ownedBuild(Long buildId, Long memberId) {
        if (buildId == null) {
            return null;
        }
        KeyboardBuild build = builds.findWithMember(buildId).orElseThrow(BuildNotOwnedException::new);
        if (!Objects.equals(build.getMember().getId(), memberId)) {
            throw new BuildNotOwnedException();
        }
        return build;
    }

    private static Long buildIdOf(KeyboardBuild build) {
        return build == null ? null : build.getId();
    }

    /** 필수 항목(체감 4항목과 별점)과 범위를 서비스에서도 확인한다. 태그·윤활은 선택이다. */
    static void validate(RatingInput in) {
        if (in.soundLevel() == null || in.soundTone() == null || in.feel() == null
                || in.fatigueLevel() == null || in.starRating() == null) {
            throw new IllegalArgumentException("체감 항목과 별점을 모두 골라 주세요");
        }
        if (!inScale(in.soundLevel()) || !inScale(in.fatigueLevel()) || !inScale(in.starRating())) {
            throw new IllegalArgumentException("1~5 사이로 골라 주세요");
        }
        if (in.tags() != null && in.tags().size() > MAX_TAGS) {
            throw new IllegalArgumentException("개조 태그는 " + MAX_TAGS + "개까지 달 수 있어요");
        }
    }

    private static boolean inScale(Short value) {
        return value >= 1 && value <= 5;
    }

    /** 공백 정리 + 소문자 키로 같은 태그를 찾고, 없으면 처음 입력한 표시 형태로 만든다(Q5). */
    private Set<ModTag> resolveTags(List<String> raw) {
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (String s : raw == null ? List.<String>of() : raw) {
            String name = normalize(s);
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > TAG_MAX_LENGTH) {
                throw new IllegalArgumentException("태그는 " + TAG_MAX_LENGTH + "자 이하로 입력해 주세요");
            }
            byKey.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        Set<ModTag> result = new LinkedHashSet<>();
        for (var entry : byKey.entrySet()) {
            result.add(tags.findByNormalized(entry.getKey())
                    .orElseGet(() -> createOrReuse(entry.getValue(), entry.getKey())));
        }
        return result;
    }

    /**
     * 같은 키를 동시에 만들면 한쪽이 유니크 위반을 맞는다. 그때는 상대가 넣은 태그를 다시 찾아 쓴다.
     * 다시 찾아도 없으면 충돌로 보고 409가 되게 한다.
     */
    private ModTag createOrReuse(String name, String normalized) {
        try {
            return tagWriter.insert(name, normalized);
        } catch (DataIntegrityViolationException e) {
            return tags.findByNormalized(normalized)
                    .orElseThrow(() -> new OptimisticLockingFailureException("태그를 함께 만들고 있어요: " + normalized, e));
        }
    }

    private Rating saveRating(Rating r) {
        try {
            return ratings.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // 유니크(스위치·회원·빌드) 충돌이 가장 흔하다. 그 밖의 제약 위반도 입력 오류로 보고 같은 안내를 준다.
            throw new RatingDuplicateException();
        }
    }

    static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }
}
