package com.switchbase.sounds;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SoundViewTest {

    private static SoundView view(long id, String channel) {
        return new SoundView(id, "dQw4w9WgXcQ", 0, "0:00", channel, "닉", List.of(), false, false);
    }

    @Test
    void startLabelFormatsMinutesAndHours() {
        assertThat(SoundView.startLabel(0)).isEqualTo("0:00");
        assertThat(SoundView.startLabel(83)).isEqualTo("1:23");
        assertThat(SoundView.startLabel(3723)).isEqualTo("1:02:03");
    }

    @Test
    void groupsByChannelAndPutsBiggerChannelFirst() {
        // 입력은 최신순. 채널 B가 샘플 2개로 가장 많으므로 먼저 온다.
        List<SoundView> input = List.of(view(1, "A"), view(2, "B"), view(3, "C"), view(4, "B"));

        List<SoundView.Group> groups = SoundView.groupByChannel(input);

        assertThat(groups).extracting(SoundView.Group::channelName).containsExactly("B", "A", "C");
        assertThat(groups.get(0).samples()).extracting(SoundView::id).containsExactly(2L, 4L);
    }

    @Test
    void emptyInputGivesNoGroups() {
        assertThat(SoundView.groupByChannel(List.of())).isEmpty();
    }
}
