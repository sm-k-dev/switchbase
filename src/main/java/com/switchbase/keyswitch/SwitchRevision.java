package com.switchbase.keyswitch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "switch_revision")
public class SwitchRevision {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id")
    private KeyboardSwitch keyboardSwitch;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Embedded
    private SwitchSpec spec;

    @Column (name = "edit_summary", nullable = false, length = 200)
    private String editSummary;

    @Column (name = "editor", nullable = false, length = 50)
    private String editor;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SwitchRevision () {}

    public SwitchRevision (KeyboardSwitch keyboardSwitch, int revisionNo, String editSummary, String editor) {
        this.keyboardSwitch = keyboardSwitch;
        this.revisionNo = revisionNo;
        this.spec = keyboardSwitch.getSpec();
        this.editSummary = editSummary;
        this.editor = editor;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public SwitchSpec getSpec() {
        return spec;
    }

    public String getEditSummary() {
        return editSummary;
    }

    public String getEditor() {
        return editor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
