package com.switchbase.sounds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 타건음 샘플 등록 폼 (sound-compare-design §5.1). */
public class SoundForm {

    @NotBlank(message = "유튜브 주소를 적어 주세요.")
    @Size(max = 300, message = "주소가 너무 길어요.")
    private String youtube;

    @Size(max = 20, message = "시작 시간을 다시 적어 주세요.")
    private String startTime;

    @NotBlank(message = "채널 이름을 적어 주세요.")
    @Size(max = 100, message = "채널 이름은 100자 이하로 적어 주세요.")
    private String channelName;

    private List<String> conditions = new ArrayList<>();

    /** 선택지에 없는 코드는 IllegalArgumentException. */
    public Set<SoundCondition> conditionSet() {
        Set<SoundCondition> result = EnumSet.noneOf(SoundCondition.class);
        if (conditions != null) {
            for (String code : conditions) {
                if (code != null && !code.isBlank()) {
                    result.add(SoundCondition.valueOf(code.strip()));
                }
            }
        }
        return result;
    }

    public String getYoutube() { return youtube; }
    public void setYoutube(String youtube) { this.youtube = youtube; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions; }
}
