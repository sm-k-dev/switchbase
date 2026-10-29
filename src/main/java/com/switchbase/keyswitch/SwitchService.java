package com.switchbase.keyswitch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class SwitchService {

    private final KeyboardSwitchRepository switches;
    private final SwitchRevisionRepository revisions;
    private final SpecSourceRepository sources;

    public SwitchService (KeyboardSwitchRepository switches,
                          SwitchRevisionRepository revisions,
                          SpecSourceRepository sources) {
        this.switches = switches;
        this.revisions = revisions;
        this.sources = sources;
    }

    public Page<KeyboardSwitch> list(Pageable pageable) {
        return switches.findAll(pageable);
    }

    public KeyboardSwitch get(Long id) {
        return switches.findById(id).orElseThrow(() -> new NoSuchElementException("스위치가 없습니다 : " + id));
    }

    public List<SwitchRevision> revisionsOf(Long id) {
        return revisions.findByKeyboardSwitchIdOrderByRevisionNoDesc(id);
    }

    public List<SpecSource> sourcesOf(Long id) {
        return sources.findByKeyboardSwitchIdOrderByIdAsc(id);
    }
}
