package com.switchbase.reviews;

import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 후기 (스위치당 1건, 수정으로 갱신. Q2). 소프트 딜리트·숨김 상태를 가진다. */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "switch_id", nullable = false)
    private KeyboardSwitch keyboardSwitch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    /** 추천 대상 코드를 쉼표로 이은 값 (RecommendFor.name()). */
    @Column(name = "recommended_for", length = 100)
    private String recommendedFor;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() {}

    Review(KeyboardSwitch sw, Member member, String body, List<RecommendFor> recommended) {
        this.keyboardSwitch = sw;
        this.member = member;
        this.createdAt = Instant.now();
        apply(body, recommended);
    }

    void apply(String body, List<RecommendFor> recommended) {
        this.body = body;
        this.recommendedFor = recommended.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(null);
        this.updatedAt = Instant.now();
    }

    void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    void changeHidden(boolean hidden) {
        this.hidden = hidden;
        this.updatedAt = Instant.now();
    }

    void changeHelpfulCount(int count) {
        this.helpfulCount = count;
    }

    /** 저장된 코드 문자열을 선택지 목록으로. 옛 값이 없으면 빈 목록. */
    public List<RecommendFor> recommendations() {
        List<RecommendFor> result = new ArrayList<>();
        if (recommendedFor != null && !recommendedFor.isBlank()) {
            for (String code : recommendedFor.split(",")) {
                result.add(RecommendFor.valueOf(code.strip()));
            }
        }
        return result;
    }

    public Long getId() { return id; }
    public KeyboardSwitch getKeyboardSwitch() { return keyboardSwitch; }
    public Member getMember() { return member; }
    public String getBody() { return body; }
    public int getHelpfulCount() { return helpfulCount; }
    public boolean isHidden() { return hidden; }
    public boolean isDeleted() { return deletedAt != null; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
