package com.switchbase.sounds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeRefTest {

    private static final String ID = "dQw4w9WgXcQ";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&t=83",
            "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "youtu.be/dQw4w9WgXcQ",
            "dQw4w9WgXcQ"
    })
    void acceptsCommonFormsAndExtractsId(String input) {
        assertThat(YoutubeRef.videoId(input)).isEqualTo(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/channel/UCxxxx",
            "https://www.youtube.com/watch?v=short",
            "https://youtu.be/",
            "not a url"
    })
    void rejectsOtherAddresses(String input) {
        assertThatThrownBy(() -> YoutubeRef.videoId(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startTimeAcceptsSecondsMinutesAndHours() {
        assertThat(YoutubeRef.startSeconds("83")).isEqualTo(83);
        assertThat(YoutubeRef.startSeconds("1:23")).isEqualTo(83);
        assertThat(YoutubeRef.startSeconds("1:02:03")).isEqualTo(3723);
        assertThat(YoutubeRef.startSeconds("0:05")).isEqualTo(5);
        assertThat(YoutubeRef.startSeconds("  ")).isZero();
        assertThat(YoutubeRef.startSeconds(null)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "1:99", "abc", "1:2:3", "100:00:00", "1234567"})
    void startTimeRejectsBadValues(String input) {
        assertThatThrownBy(() -> YoutubeRef.startSeconds(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
