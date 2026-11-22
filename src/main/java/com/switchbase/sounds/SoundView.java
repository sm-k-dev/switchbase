package com.switchbase.sounds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스위치 상세의 샘플 한 줄 (sound-compare-design §5.2). 이메일은 없다. 작성자는 닉네임만.
 * hidden은 작성자 본인에게만 보이는 숨김 상태, mine은 작성자 여부다.
 */
public record SoundView(
        Long id,
        String youtubeId,
        int startSeconds,
        String startLabel,
        String channelName,
        String uploaderName,
        List<String> conditionLabels,
        boolean hidden,
        boolean mine) {

    /** 채널 한 묶음. 샘플이 많은 채널이 먼저 온다. */
    public record Group(String channelName, List<SoundView> samples) {
    }

    static SoundView from(SoundSample s, Long viewerId) {
        boolean withdrawn = s.getMember() == null;
        Long authorId = withdrawn ? null : s.getMember().getId();
        List<String> labels = new ArrayList<>();
        s.getConditions().forEach(c -> labels.add(c.getLabel()));
        return new SoundView(
                s.getId(),
                s.getYoutubeId(),
                s.getStartSeconds(),
                startLabel(s.getStartSeconds()),
                s.getChannelName(),
                withdrawn ? "탈퇴한 회원" : s.getMember().getDisplayNickname(),
                labels,
                s.isHidden(),
                viewerId != null && viewerId.equals(authorId));
    }

    /** 83 → "1:23", 3723 → "1:02:03". */
    public static String startLabel(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0
                ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }

    /** 입력 순서(최신순)를 지키면서 채널별로 묶고, 샘플 수가 많은 채널을 앞에 둔다. */
    public static List<Group> groupByChannel(List<SoundView> views) {
        Map<String, List<SoundView>> byChannel = new LinkedHashMap<>();
        for (SoundView v : views) {
            byChannel.computeIfAbsent(v.channelName(), k -> new ArrayList<>()).add(v);
        }
        List<Group> groups = new ArrayList<>();
        byChannel.forEach((name, list) -> groups.add(new Group(name, list)));
        groups.sort((a, b) -> Integer.compare(b.samples().size(), a.samples().size()));
        return groups;
    }
}
