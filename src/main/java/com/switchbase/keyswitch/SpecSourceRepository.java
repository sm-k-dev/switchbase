package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpecSourceRepository extends JpaRepository<SpecSource, Long> {

    List<SpecSource> findByKeyboardSwitchIdOrderByIdAsc(Long switchId);
}