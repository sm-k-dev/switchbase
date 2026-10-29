package com.switchbase.keyswitch;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class SwitchService {

    private final KeyboardSwitchRepository switches;
    private final SwitchRevisionRepository revisions;
    private final SpecSourceRepository sources;
    private final SwitchQueryRepository switchQuery;

    public SwitchService(KeyboardSwitchRepository switches,
                         SwitchRevisionRepository revisions,
                         SpecSourceRepository sources,
                         SwitchQueryRepository switchQuery) {
        this.switches = switches;
        this.revisions = revisions;
        this.sources = sources;
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

    public boolean exists(String manufacturer, String modelName) {
        return switches.existsBySpecManufacturerAndSpecModelName(manufacturer, modelName);
    }

    @Transactional
    public KeyboardSwitch create(SwitchSpec spec, String editSummary, String editor, String sourceUrl) {
        KeyboardSwitch saved = switches.save(new KeyboardSwitch(spec));
        revisions.save(new SwitchRevision(saved, 1, editSummary, editor));
        addSource(saved, sourceUrl);
        return saved;
    }

    @Transactional
    public KeyboardSwitch update(Long id, Long expectedVersion, SwitchSpec spec,
                                 String editSummary, String editor, String sourceUrl) {
        KeyboardSwitch target = get(id);
        if (expectedVersion == null || target.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("다른 사람이 먼저 수정했습니다: " + id);
        }
        target.changeSpec(spec);
        int next = revisions.countByKeyboardSwitchId(id) + 1;
        revisions.save(new SwitchRevision(target, next, editSummary, editor));
        addSource(target, sourceUrl);
        return target;
    }

    private void addSource(KeyboardSwitch target, String sourceUrl) {
        if (StringUtils.hasText(sourceUrl)) {
            sources.save(new SpecSource(target, SourceType.OFFICIAL, sourceUrl.strip(), null));
        }
    }
}