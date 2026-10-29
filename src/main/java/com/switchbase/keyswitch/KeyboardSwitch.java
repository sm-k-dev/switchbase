package com.switchbase.keyswitch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "keyboard_switch")
public class KeyboardSwitch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private SwitchSpec spec;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KeyboardSwitch() {}

    public KeyboardSwitch(SwitchSpec spec) {
        this.spec = spec;
    }

    public void changeSpec(SwitchSpec spec) {
        this.spec = spec;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public SwitchSpec getSpec() { return spec; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
