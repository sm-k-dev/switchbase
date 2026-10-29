package com.switchbase.keyswitch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "spec_source")
public class SpecSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id")
    private KeyboardSwitch keyboardSwitch;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SpecSource() {
    }

    public SpecSource(KeyboardSwitch keyboardSwitch, SourceType sourceType, String url, String note) {
        this.keyboardSwitch = keyboardSwitch;
        this.sourceType = sourceType;
        this.url = url;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getUrl() { return url; }
    public String getNote() { return note; }
}
