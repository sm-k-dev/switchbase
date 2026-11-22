package com.switchbase.sounds;

import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** 타건음 샘플 1개. 유튜브 영상 id와 시작 시간만 가진다. 음원 파일은 없다. */
@Entity
@Table(name = "sound_sample")
public class SoundSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id", nullable = false)
    private KeyboardSwitch keyboardSwitch;

    /** 등록자. 탈퇴 등으로 연결이 끊기면 null이 된다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "youtube_id", nullable = false, length = 11)
    private String youtubeId;

    @Column(name = "start_seconds", nullable = false)
    private int startSeconds;

    @Column(name = "channel_name", nullable = false, length = 100)
    private String channelName;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sound_sample_condition", joinColumns = @JoinColumn(name = "sound_sample_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_code", nullable = false, length = 30)
    private Set<SoundCondition> conditions = EnumSet.noneOf(SoundCondition.class);

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SoundSample() {}

    SoundSample(KeyboardSwitch sw, Member member, String youtubeId, int startSeconds,
                String channelName, Set<SoundCondition> conditions) {
        this.keyboardSwitch = sw;
        this.member = member;
        this.youtubeId = youtubeId;
        this.startSeconds = startSeconds;
        this.channelName = channelName;
        this.conditions = conditions.isEmpty()
                ? EnumSet.noneOf(SoundCondition.class)
                : EnumSet.copyOf(conditions);
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public KeyboardSwitch getKeyboardSwitch() { return keyboardSwitch; }
    public Member getMember() { return member; }
    public String getYoutubeId() { return youtubeId; }
    public int getStartSeconds() { return startSeconds; }
    public String getChannelName() { return channelName; }
    public Set<SoundCondition> getConditions() { return conditions; }
    public boolean isHidden() { return hidden; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant getCreatedAt() { return createdAt; }
}
