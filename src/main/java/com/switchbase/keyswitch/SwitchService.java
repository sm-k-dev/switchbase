package com.switchbase.keyswitch;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
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
                                 String editSummary, String editor, String sourceUrl) {
        KeyboardSwitch saved = switches.save(new KeyboardSwitch(canonical(spec)));
        revisions.save(new SwitchRevision(saved, 1, editSummary, editor));
        replaceAliases(saved, aliasNames);
        addSource(saved, sourceUrl);
        return saved;
    }

    @Transactional
    public KeyboardSwitch update(Long id, Long expectedVersion, SwitchSpec spec, List<String> aliasNames,
                                 String editSummary, String editor, String sourceUrl) {
        KeyboardSwitch target = get(id);
        if (expectedVersion == null || target.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다: " + id);
        }
        target.changeSpec(canonical(spec));
        int next = revisions.countByKeyboardSwitchId(id) + 1;
        revisions.save(new SwitchRevision(target, next, editSummary, editor));
        replaceAliases(target, aliasNames);
        addSource(target, sourceUrl);
        return target;
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