package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SwitchAliasRepository extends JpaRepository<SwitchAlias, Long> {

    List<SwitchAlias> findByKeyboardSwitchIdOrderByIdAsc(Long switchId);

    void deleteByKeyboardSwitchId(Long switchId);
}