package com.switchbase.keyswitch;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public record SwitchSpec (
        @Column(name = "manufacturer", nullable = false, length = 100) String manufacturer,
        @Column(name = "model_name", nullable = false, length = 150) String modelName,
        @Enumerated(EnumType.STRING)
  @Column(name = "switch_type", nullable = false, length = 20) SwitchType switchType,
        @Column(name = "actuation_force_g", precision = 5, scale = 1) BigDecimal actuationForceG,
        @Column(name = "bottom_out_force_g", precision = 5, scale = 1) BigDecimal bottomOutForceG,
        @Column(name = "pre_travel_mm", precision = 3, scale = 1) BigDecimal preTravelMm,
        @Column(name = "total_travel_mm", precision = 3, scale = 1) BigDecimal totalTravelMm,
        @Column(name = "release_year") Short releaseYear,
        @Column(name = "description", columnDefinition = "text") String description
){}
