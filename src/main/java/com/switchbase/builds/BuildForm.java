package com.switchbase.builds;

import jakarta.validation.constraints.Size;

/** 등록·수정 폼. 고정 선택지 값은 서비스에서 범위를 확인한다. */
public class BuildForm {

    @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    private String name;

    private String housingMaterial;
    private String plateMaterial;
    private String mountType;
    private String keycapMaterial;

    @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
    private String memo;

    /** 수정 화면을 연 순간의 버전. 등록에서는 비운다. */
    private Long version;

    public static BuildForm from(KeyboardBuild b) {
        BuildForm f = new BuildForm();
        f.name = b.getName();
        f.housingMaterial = b.getHousingMaterial();
        f.plateMaterial = b.getPlateMaterial();
        f.mountType = b.getMountType();
        f.keycapMaterial = b.getKeycapMaterial();
        f.memo = b.getMemo();
        f.version = b.getVersion();
        return f;
    }

    public BuildInput toInput() {
        return new BuildInput(name, housingMaterial, plateMaterial, mountType, keycapMaterial, memo);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHousingMaterial() { return housingMaterial; }
    public void setHousingMaterial(String housingMaterial) { this.housingMaterial = housingMaterial; }
    public String getPlateMaterial() { return plateMaterial; }
    public void setPlateMaterial(String plateMaterial) { this.plateMaterial = plateMaterial; }
    public String getMountType() { return mountType; }
    public void setMountType(String mountType) { this.mountType = mountType; }
    public String getKeycapMaterial() { return keycapMaterial; }
    public void setKeycapMaterial(String keycapMaterial) { this.keycapMaterial = keycapMaterial; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
