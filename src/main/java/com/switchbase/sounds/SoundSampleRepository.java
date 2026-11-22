package com.switchbase.sounds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SoundSampleRepository extends JpaRepository<SoundSample, Long> {

    boolean existsByKeyboardSwitchIdAndYoutubeIdAndStartSecondsAndDeletedAtIsNull(
            Long switchId, String youtubeId, int startSeconds);

    /** 숨김 샘플은 작성자 본인에게만 보인다. viewerId가 없으면 -1을 넘긴다. */
    @Query("select s from SoundSample s left join s.member m "
            + "where s.keyboardSwitch.id = :switchId and s.deletedAt is null "
            + "and (s.hidden = false or m.id = :viewerId) "
            + "order by s.createdAt desc, s.id desc")
    List<SoundSample> findVisible(@Param("switchId") Long switchId, @Param("viewerId") Long viewerId);
}
