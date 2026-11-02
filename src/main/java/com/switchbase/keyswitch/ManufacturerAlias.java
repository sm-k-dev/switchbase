package com.switchbase.keyswitch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 제조사의 다른 표기와 대표 이름의 짝. 데이터는 마이그레이션 SQL로 넣는다. */
@Entity
@Table(name = "manufacturer_alias")
public class ManufacturerAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manufacturer", nullable = false, length = 100)
    private String manufacturer;

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "normalized", nullable = false, length = 100)
    private String normalized;

    protected ManufacturerAlias() {}

    public Long getId() { return id; }
    public String getManufacturer() { return manufacturer; }
    public String getAlias() { return alias; }
}