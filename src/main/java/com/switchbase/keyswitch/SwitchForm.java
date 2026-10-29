package com.switchbase.keyswitch;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 관리자 등록/수정 화면의 입력값. 검증 규칙은 여기에 모은다. */
public class SwitchForm {

    @NotBlank @Size(max = 100)
    private String manufacturer;

    @NotBlank @Size(max = 150)
    private String modelName;

    @NotNull
    private SwitchType switchType;

    @DecimalMin("10.0") @DecimalMax("200.0")
    private BigDecimal actuationForceG;

    @DecimalMin("10.0") @DecimalMax("200.0")
    private BigDecimal bottomOutForceG;

    @DecimalMin("0.1") @DecimalMax("9.9")
    private BigDecimal preTravelMm;

    @DecimalMin("0.1") @DecimalMax("9.9")
    private BigDecimal totalTravelMm;

    @Min(1980) @Max(2100)
    private Short releaseYear;

    @Size(max = 5000)
    private String description;

    @NotBlank(message = "무엇을 바꿨는지 한 줄 적어 주세요")
    @Size(max = 200)
    private String editSummary;

    @Size(max = 500)
    @Pattern(regexp = "^$|^https?://.+", message = "http:// 또는 https://로 시작해야 해요")
    private String sourceUrl;

    // 수정 화면을 연 순간의 버전. 저장할 때 DB 버전과 다르면 그 사이 누가 고친 것이다.
    private Long version;

    public static SwitchForm from(KeyboardSwitch sw) {
        SwitchSpec s = sw.getSpec();
        SwitchForm f = new SwitchForm();
        f.version = sw.getVersion();
        f.manufacturer = s.manufacturer();
        f.modelName = s.modelName();
        f.switchType = s.switchType();
        f.actuationForceG = s.actuationForceG();
        f.bottomOutForceG = s.bottomOutForceG();
        f.preTravelMm = s.preTravelMm();
        f.totalTravelMm = s.totalTravelMm();
        f.releaseYear = s.releaseYear();
        f.description = s.description();
        return f;
    }

    public SwitchSpec toSpec() {
        return new SwitchSpec(manufacturer.strip(), modelName.strip(), switchType,
                actuationForceG, bottomOutForceG, preTravelMm, totalTravelMm,
                releaseYear, description);
    }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public SwitchType getSwitchType() { return switchType; }
    public void setSwitchType(SwitchType switchType) { this.switchType = switchType; }
    public BigDecimal getActuationForceG() { return actuationForceG; }
    public void setActuationForceG(BigDecimal actuationForceG) { this.actuationForceG = actuationForceG; }
    public BigDecimal getBottomOutForceG() { return bottomOutForceG; }
    public void setBottomOutForceG(BigDecimal bottomOutForceG) { this.bottomOutForceG = bottomOutForceG; }
    public BigDecimal getPreTravelMm() { return preTravelMm; }
    public void setPreTravelMm(BigDecimal preTravelMm) { this.preTravelMm = preTravelMm; }
    public BigDecimal getTotalTravelMm() { return totalTravelMm; }
    public void setTotalTravelMm(BigDecimal totalTravelMm) { this.totalTravelMm = totalTravelMm; }
    public Short getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Short releaseYear) { this.releaseYear = releaseYear; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEditSummary() { return editSummary; }
    public void setEditSummary(String editSummary) { this.editSummary = editSummary; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}