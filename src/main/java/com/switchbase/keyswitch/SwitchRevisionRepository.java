package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SwitchRevisionRepository extends JpaRepository<SwitchRevision, Long> {

    List<SwitchRevision> findByKeyboardSwitchIdOrderByRevisionNoDesc(Long switchId);

    int countByKeyboardSwitchId(Long switchId);
}