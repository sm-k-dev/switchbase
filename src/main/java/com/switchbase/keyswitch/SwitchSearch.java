package com.switchbase.keyswitch;

import java.math.BigDecimal;

public record SwitchSearch (String keyword,
                           SwitchType type,
                           BigDecimal minForce,
                           BigDecimal maxForce) {}
