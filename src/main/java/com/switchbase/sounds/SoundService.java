package com.switchbase.sounds;

import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.keyswitch.KeyboardSwitchRepository;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** 타건음 샘플 등록 (sound-compare-design §4, §5.1). 등록은 로그인 회원 누구나. */
@Service
public class SoundService {

    private final SoundSampleRepository samples;
    private final KeyboardSwitchRepository switches;
    private final MemberRepository members;

    public SoundService(SoundSampleRepository samples, KeyboardSwitchRepository switches,
                        MemberRepository members) {
        this.samples = samples;
        this.switches = switches;
        this.members = members;
    }

    /** 스위치 상세의 샘플 목록. 누구나 볼 수 있고, 채널별로 묶어 돌려준다. */
    @Transactional(readOnly = true)
    public List<SoundView.Group> list(Long switchId, Long viewerId) {
        if (!switches.existsById(switchId)) {
            throw new NoSuchElementException("스위치가 없습니다: " + switchId);
        }
        List<SoundView> views = samples.findVisible(switchId, viewerId == null ? -1L : viewerId).stream()
                .map(s -> SoundView.from(s, viewerId))
                .toList();
        return SoundView.groupByChannel(views);
    }

    /**
     * 샘플을 등록한다. 주소와 시작 시간이 맞지 않으면 IllegalArgumentException,
     * 같은 영상과 시작 시간이 이미 있으면 SoundDuplicateException.
     */
    @Transactional
    public SoundSample register(Long switchId, Long memberId, String youtubeInput, String startInput,
                                String channelName, Set<SoundCondition> conditions) {
        KeyboardSwitch sw = switches.findById(switchId)
                .orElseThrow(() -> new NoSuchElementException("스위치가 없습니다: " + switchId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원이 없습니다: " + memberId));

        String videoId = YoutubeRef.videoId(youtubeInput);
        int start = YoutubeRef.startSeconds(startInput);
        if (samples.existsByKeyboardSwitchIdAndYoutubeIdAndStartSecondsAndDeletedAtIsNull(switchId, videoId, start)) {
            throw new SoundDuplicateException();
        }
        try {
            return samples.saveAndFlush(new SoundSample(sw, member, videoId, start,
                    channelName.strip(), conditions));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 샘플을 올린 경우: 부분 유니크 인덱스가 막는다
            throw new SoundDuplicateException();
        }
    }
}
