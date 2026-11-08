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

    /** 편집자 닉네임 스냅샷. 이메일은 넣지 않는다(V5, wiki-design §3). 기존 행은 이메일이 남아 있어도 표시하지 않는다. */
    @Column (name = "editor", nullable = false, length = 50)
    private String editor;

    /** 편집자 회원 id. 기존 행(V5 이전)은 null이며 화면에서는 "초기 관리자"로 나온다. */
    @Column (name = "editor_member_id")
    private Long editorMemberId;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SwitchRevision () {}

    public SwitchRevision (KeyboardSwitch keyboardSwitch, int revisionNo, String editSummary,
                           Long editorMemberId, String editorNickname) {
        this.keyboardSwitch = keyboardSwitch;
        this.revisionNo = revisionNo;
        this.spec = keyboardSwitch.getSpec();
        this.editSummary = editSummary;
        this.editorMemberId = editorMemberId;
        this.editor = editorNickname;
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

    public Long getEditorMemberId() {
        return editorMemberId;
    }

    /** 화면에 보이는 편집자 이름. 회원이 연결되지 않은 행은 "초기 관리자"로 보인다. 이메일은 나오지 않는다. */
    public String getEditorDisplay() {
        return editorMemberId == null ? "초기 관리자" : editor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
