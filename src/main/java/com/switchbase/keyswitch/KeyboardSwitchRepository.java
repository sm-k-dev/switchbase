package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyboardSwitchRepository extends JpaRepository<KeyboardSwitch, Long> {

    boolean existsBySpecManufacturerAndSpecModelName(String manufacturer, String modelName);
}