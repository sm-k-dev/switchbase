package com.switchbase.keyswitch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyboardSwitchRepository extends JpaRepository<KeyboardSwitch, Long> {

    boolean existsBySpecManufacturerAndSpecModelNameAndSpecVariant(String manufacturer, String modelName, String variant);

    boolean existsBySpecManufacturerAndSpecModelNameAndSpecVariantIsNull(String manufacturer, String modelName);

}