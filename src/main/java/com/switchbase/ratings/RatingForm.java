package com.switchbase.ratings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 평가 등록·수정 폼 (rating-screens §2). 필수 항목은 @NotNull·범위로, 나머지는 서비스에서 확인한다. */
public class RatingForm {

    /** 수정 화면을 연 순간의 버전. 등록에서는 비운다. */
    private Long version;

    /** null이면 빌드 없이 평가한다. */
    private Long buildId;

    @NotNull @Min(1) @Max(5)
    private Short soundLevel;

    @NotNull
    private SoundTone soundTone;

    @NotNull
    private Feel feel;

    @NotNull @Min(1) @Max(5)
    private Short fatigueLevel;

    @NotNull @Min(1) @Max(5)
    private Short starRating;

    /** null = 미입력 */
    private Boolean lubed;

    /** 쉼표로 구분한 개조 태그. 자동완성은 마지막 토큰 기준. */
    private String modTags;

    public static RatingForm from(Rating r) {
        RatingForm f = new RatingForm();
        f.version = r.getVersion();
        f.buildId = r.getBuild() == null ? null : r.getBuild().getId();
        f.soundLevel = r.getSoundLevel();
        f.soundTone = r.getSoundTone();
        f.feel = r.getFeel();
        f.fatigueLevel = r.getFatigueLevel();
        f.starRating = r.getStarRating();
        f.lubed = r.getLubed();
        f.modTags = r.getModTags().stream().map(t -> t.getName()).collect(Collectors.joining(", "));
        return f;
    }

    public RatingInput toInput() {
        return new RatingInput(buildId, soundLevel, soundTone, feel, fatigueLevel, starRating, lubed, parseTags(modTags));
    }

    /** 쉼표로 나눈 태그 목록. 빈 토큰은 버린다. */
    static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getBuildId() { return buildId; }
    public void setBuildId(Long buildId) { this.buildId = buildId; }
    public Short getSoundLevel() { return soundLevel; }
    public void setSoundLevel(Short soundLevel) { this.soundLevel = soundLevel; }
    public SoundTone getSoundTone() { return soundTone; }
    public void setSoundTone(SoundTone soundTone) { this.soundTone = soundTone; }
    public Feel getFeel() { return feel; }
    public void setFeel(Feel feel) { this.feel = feel; }
    public Short getFatigueLevel() { return fatigueLevel; }
    public void setFatigueLevel(Short fatigueLevel) { this.fatigueLevel = fatigueLevel; }
    public Short getStarRating() { return starRating; }
    public void setStarRating(Short starRating) { this.starRating = starRating; }
    public Boolean getLubed() { return lubed; }
    public void setLubed(Boolean lubed) { this.lubed = lubed; }
    public String getModTags() { return modTags; }
    public void setModTags(String modTags) { this.modTags = modTags; }
}
