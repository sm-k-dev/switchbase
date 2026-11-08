package com.switchbase.keyswitch;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SwitchService {

    private final KeyboardSwitchRepository switches;
    private final SwitchRevisionRepository revisions;
    private final SpecSourceRepository sources;
    private final SwitchAliasRepository aliases;
    private final ManufacturerAliasRepository manufacturerAliases;
    private final SwitchQueryRepository switchQuery;

    public SwitchService(KeyboardSwitchRepository switches,
                         SwitchRevisionRepository revisions,
                         SpecSourceRepository sources,
                         SwitchAliasRepository aliases,
                         ManufacturerAliasRepository manufacturerAliases,
                         SwitchQueryRepository switchQuery) {
        this.switches = switches;
        this.revisions = revisions;
        this.sources = sources;
        this.aliases = aliases;
        this.manufacturerAliases = manufacturerAliases;
        this.switchQuery = switchQuery;
    }

    public Page<KeyboardSwitch> search(SwitchSearch search, Pageable pageable) {
        return switchQuery.search(search, pageable);
    }

    public KeyboardSwitch get(Long id) {
        return switches.findById(id)
                .orElseThrow(() -> new NoSuchElementException("스위치가 없습니다: " + id));
    }

    public List<SwitchRevision> revisionsOf(Long id) {
        return revisions.findByKeyboardSwitchIdOrderByRevisionNoDesc(id);
    }

    public List<SpecSource> sourcesOf(Long id) {
        return sources.findByKeyboardSwitchIdOrderByIdAsc(id);
    }

    public List<SwitchAlias> aliasesOf(Long id) {
        return aliases.findByKeyboardSwitchIdOrderByIdAsc(id);
    }

    /** "게이트론"처럼 등록된 다른 표기면 대표 이름("Gateron")으로 바꾼다. 모르는 이름은 그대로. */
    public String canonicalManufacturer(String manufacturer) {
        return manufacturerAliases.findByNormalized(Names.normalize(manufacturer))
                .map(ManufacturerAlias::getManufacturer)
                .orElse(manufacturer.strip());
    }

    /** 제조사 + 이름 + 변형이 모두 같은 스위치가 있는지. 변형이 비어 있으면 변형 없는 것끼리 비교한다. */
    public boolean exists(String manufacturer, String modelName, String variant) {
        String official = canonicalManufacturer(manufacturer);
        if (variant == null || variant.isBlank()) {
            return switches.existsBySpecManufacturerAndSpecModelNameAndSpecVariantIsNull(official, modelName);
        }
        return switches.existsBySpecManufacturerAndSpecModelNameAndSpecVariant(official, modelName, variant.strip());
    }

    @Transactional
    public KeyboardSwitch create(SwitchSpec spec, List<String> aliasNames,
                                 String editSummary, Long editorMemberId, String editorNickname, String sourceUrl) {
        KeyboardSwitch saved = switches.save(new KeyboardSwitch(canonical(spec)));
        revisions.save(new SwitchRevision(saved, 1, editSummary, editorMemberId, editorNickname));
        replaceAliases(saved, aliasNames);
        addSource(saved, sourceUrl);
        return saved;
    }

    @Transactional
    public KeyboardSwitch update(Long id, Long expectedVersion, SwitchSpec spec, List<String> aliasNames,
                                 String editSummary, Long editorMemberId, String editorNickname, String sourceUrl) {
        KeyboardSwitch target = lockForUpdate(id);
        if (expectedVersion == null || target.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다: " + id);
        }
        target.changeSpec(canonical(spec));
        int next = revisions.countByKeyboardSwitchId(id) + 1;
        saveRevision(new SwitchRevision(target, next, editSummary, editorMemberId, editorNickname));
        replaceAliases(target, aliasNames);
        addSource(target, sourceUrl);
        return target;
    }

    /** 되돌리기를 막는 이유. 막히지 않으면 null. 화면의 버튼 표시와 서버 검사에 같이 쓴다. */
    public RevertBlock revertBlock(Long id, int revisionNo, Long memberId, boolean admin) {
        List<SwitchRevision> all = revisionsOf(id);
        SwitchRevision target = all.stream()
                .filter(r -> r.getRevisionNo() == revisionNo)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("리비전이 없습니다: " + id + " #" + revisionNo));
        if (revisionNo == 1) {
            return RevertBlock.FIRST_REVISION;
        }
        if (all.getFirst().getRevisionNo() != revisionNo) {
            return RevertBlock.NOT_LATEST;
        }
        boolean author = memberId != null && memberId.equals(target.getEditorMemberId());
        if (!admin && !author) {
            return RevertBlock.NOT_ALLOWED;
        }
        return null;
    }

    /**
     * 최신 수정을 직전 스냅샷으로 되돌리는 새 리비전을 남긴다. 권한·최신·첫 리비전을 서비스에서 다시 확인한다.
     * 별칭·출처는 되돌리지 않는다(wiki-design Q3).
     */
    @Transactional
    public KeyboardSwitch revert(Long id, int revisionNo, Long expectedVersion,
                                 Long memberId, boolean admin, String editorNickname, String editSummary) {
        KeyboardSwitch target = lockForUpdate(id);
        RevertBlock block = revertBlock(id, revisionNo, memberId, admin);
        if (block != null) {
            throw new RevertNotAllowedException(block);
        }
        if (expectedVersion == null || target.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다: " + id);
        }
        SwitchRevision previous = revisions.findByKeyboardSwitchIdAndRevisionNo(id, revisionNo - 1)
                .orElseThrow(() -> new NoSuchElementException("직전 리비전이 없습니다: " + id));
        target.changeSpec(previous.getSpec());
        int next = revisions.countByKeyboardSwitchId(id) + 1;
        saveRevision(new SwitchRevision(target, next, editSummary, memberId, editorNickname));
        return target;
    }

    private KeyboardSwitch lockForUpdate(Long id) {
        return switches.findForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("스위치가 없습니다: " + id));
    }

    /** 리비전 번호가 겹치면(uk_switch_revision_no) 500 대신 충돌로 바꾼다. 잠금이 있으면 보통 여기까지 오지 않는다. */
    private void saveRevision(SwitchRevision revision) {
        try {
            revisions.saveAndFlush(revision);
        } catch (DataIntegrityViolationException e) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다", e);
        }
    }

    public SwitchRevision revisionAt(Long id, int revisionNo) {
        return revisions.findByKeyboardSwitchIdAndRevisionNo(id, revisionNo)
                .orElseThrow(() -> new NoSuchElementException("리비전이 없습니다: " + id + " #" + revisionNo));
    }

    /** 두 스냅샷의 스펙 필드를 나란히 놓는다. 설명은 여기서 빼고 화면이 전후를 따로 보여 준다. */
    public List<CompareRow> compareRows(SwitchRevision from, SwitchRevision to) {
        SwitchSpec a = from.getSpec();
        SwitchSpec b = to.getSpec();
        return List.of(
                row("제조사", a.manufacturer(), b.manufacturer()),
                row("브랜드", a.brand(), b.brand()),
                row("모델명", a.modelName(), b.modelName()),
                row("변형", a.variant(), b.variant()),
                row("타입", a.switchType() == null ? null : a.switchType().getLabel(),
                        b.switchType() == null ? null : b.switchType().getLabel()),
                row("작동압 (g)", str(a.actuationForceG()), str(b.actuationForceG())),
                row("바닥압 (g)", str(a.bottomOutForceG()), str(b.bottomOutForceG())),
                row("프리트래블 (mm)", str(a.preTravelMm()), str(b.preTravelMm())),
                row("전체 이동 (mm)", str(a.totalTravelMm()), str(b.totalTravelMm())),
                row("출시 연도", a.releaseYear() == null ? null : a.releaseYear().toString(),
                        b.releaseYear() == null ? null : b.releaseYear().toString()));
    }

    public Page<SwitchRevision> recentChanges(Pageable pageable) {
        return revisions.findAllWithSwitch(pageable);
    }

    private static CompareRow row(String label, String from, String to) {
        return new CompareRow(label, orDash(from), orDash(to), !Objects.equals(from, to));
    }

    private static String str(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private SwitchSpec canonical(SwitchSpec spec) {
        return spec.withManufacturer(canonicalManufacturer(spec.manufacturer()));
    }

    // 별칭은 통째로 갈아 끼운다. "저 월백"과 "저월백"처럼 같은 이름은 하나만 남긴다.
    private void replaceAliases(KeyboardSwitch target, List<String> aliasNames) {
        aliases.deleteByKeyboardSwitchId(target.getId());
        aliases.flush();
        Set<String> seen = new HashSet<>();
        for (String name : aliasNames) {
            if (seen.add(Names.normalize(name))) {
                aliases.save(new SwitchAlias(target, name));
            }
        }
    }

    // 처음 들어오는 출처는 공식이라고 장담할 수 없어서 커뮤니티로 둔다.
    // 나중에 유저가 공식 자료로 확인하면 OFFICIAL로 바꾼다.
    private void addSource(KeyboardSwitch target, String sourceUrl) {
        if (StringUtils.hasText(sourceUrl)) {
            sources.save(new SpecSource(target, SourceType.COMMUNITY, sourceUrl.strip(), null));
        }
    }
}