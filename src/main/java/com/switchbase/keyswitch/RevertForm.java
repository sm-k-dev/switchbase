package com.switchbase.keyswitch;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

/** 되돌리기 확인 폼. 요약을 비우면 "#N 수정 취소"로 채운다. */
public class RevertForm {

    @NotNull
    private Long version;

    @Size(max = 200)
    private String editSummary;

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getEditSummary() { return editSummary; }
    public void setEditSummary(String editSummary) { this.editSummary = editSummary; }
}
