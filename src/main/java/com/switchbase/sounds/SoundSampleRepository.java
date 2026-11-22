package com.switchbase.sounds;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SoundSampleRepository extends JpaRepository<SoundSample, Long> {

    boolean existsByKeyboardSwitchIdAndYoutubeIdAndStartSecondsAndDeletedAtIsNull(
            Long switchId, String youtubeId, int startSeconds);
}
