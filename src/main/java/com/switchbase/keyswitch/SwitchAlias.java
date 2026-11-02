package com.switchbase.keyswitch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "switch_alias")
public class SwitchAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id")
    private KeyboardSwitch keyboardSwitch;

    @Column(name = "alias", nullable = false, length = 150)
    private String alias;

    @Column(name = "normalized", nullable = false, length = 150)
    private String normalized;

    protected SwitchAlias() {
    }

    public SwitchAlias(KeyboardSwitch keyboardSwitch, String alias) {
        this.keyboardSwitch = keyboardSwitch;
        this.alias = alias.strip();
        this.normalized = Names.normalize(alias);
    }

    public Long getId() { return id; }
    public String getAlias() { return alias; }
    public String getNormalized() { return normalized; }
}