package com.switchbase.keyswitch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 레이더 그림의 점 계산 (sound-compare-design §5.3). */
class SwitchCompareRadarTest {

    @Test
    void fullScaleTouchesOuterRingOnEveryAxis() {
        // 중심 110, 반지름 90. 위쪽 점은 (110, 20)
        String points = SwitchCompareController.radarPoints(5, 5, 5);
        assertThat(points.split(" ")).hasSize(3);
        assertThat(points.split(" ")[0]).isEqualTo("110.0,20.0");
    }

    @Test
    void zeroIsTheCenter() {
        String points = SwitchCompareController.radarPoints(0, 0, 0);
        assertThat(points.split(" ")[0]).isEqualTo("110.0,110.0");
    }

    @Test
    void missingAverageGivesNoPicture() {
        SwitchCompareController.SideSummary s =
                new SwitchCompareController.SideSummary("X", 3.0, null, 4.0, 3, 5, false);
        assertThat(SwitchCompareController.radarPoints(s)).isNull();
    }
}
