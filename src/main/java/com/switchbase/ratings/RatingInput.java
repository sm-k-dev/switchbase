package com.switchbase.ratings;

import java.util.List;

/** 평가 입력. 빌드 id가 null이면 빌드 없이 평가한다. 필수 항목은 서비스에서도 확인한다. */
public record RatingInput(Long buildId, Short soundLevel, SoundTone soundTone, Feel feel,
                          Short fatigueLevel, Short starRating, Boolean lubed, List<String> tags) {
}
