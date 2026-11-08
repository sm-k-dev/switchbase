package com.switchbase.ratings;

import com.switchbase.builds.KeyboardBuild;
import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** 체감 평가 한 건. 스위치 스펙을 바꾸지 않는다(rating-design §2). */
@Entity
@Table(name = "rating")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id", nullable = false)
    private KeyboardSwitch keyboardSwitch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "build_id")
    private KeyboardBuild build;

    @Column(name = "sound_level", nullable = false)
    private Short soundLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "sound_tone", nullable = false, length = 20)
    private SoundTone soundTone;

    @Enumerated(EnumType.STRING)
    @Column(name = "feel", nullable = false, length = 20)
    private Feel feel;

    @Column(name = "fatigue_level", nullable = false)
    private Short fatigueLevel;

    @Column(name = "star_rating", nullable = false)
    private Short starRating;

    @Column(name = "lubed")
    private Boolean lubed;

    /** 신고 처리로 숨김. 작성자만 보고, 집계에서 빠진다(Q14). */
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    /** 소프트 딜리트. 집계와 목록에서 빠진다. 보관 30일 뒤 완전 삭제(§6-4). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany
    @JoinTable(name = "rating_mod_tag",
            joinColumns = @JoinColumn(name = "rating_id"),
            inverseJoinColumns = @JoinColumn(name = "mod_tag_id"))
    private Set<ModTag> modTags = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Rating() {}

    Rating(KeyboardSwitch sw, Member member, KeyboardBuild build, RatingInput in, Set<ModTag> tags) {
        this.keyboardSwitch = sw;
        this.member = member;
        this.createdAt = Instant.now();
        apply(build, in, tags);
    }

    void apply(KeyboardBuild build, RatingInput in, Set<ModTag> tags) {
        this.build = build;
        this.soundLevel = in.soundLevel();
        this.soundTone = in.soundTone();
        this.feel = in.feel();
        this.fatigueLevel = in.fatigueLevel();
        this.starRating = in.starRating();
        this.lubed = in.lubed();
        this.modTags.clear();
        this.modTags.addAll(tags);
        this.updatedAt = Instant.now();
    }

    /** 작성자 본인이 삭제하거나 관리자가 삭제한다. 행은 남기고 deleted_at을 찍는다. */
    void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    /** 신고 처리(숨김)와 작성자의 숨김 해제에만 쓴다. */
    void changeHidden(boolean hidden) {
        this.hidden = hidden;
        this.updatedAt = Instant.now();
    }

    public boolean isHidden() { return hidden; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getId() { return id; }
    public KeyboardSwitch getKeyboardSwitch() { return keyboardSwitch; }
    public Member getMember() { return member; }
    public KeyboardBuild getBuild() { return build; }
    public Short getSoundLevel() { return soundLevel; }
    public SoundTone getSoundTone() { return soundTone; }
    public Feel getFeel() { return feel; }
    public Short getFatigueLevel() { return fatigueLevel; }
    public Short getStarRating() { return starRating; }
    public Boolean getLubed() { return lubed; }
    public Set<ModTag> getModTags() { return modTags; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
