package com.switchbase.builds;

import com.switchbase.member.Member;
import jakarta.persistence.*;

import java.time.Instant;

/** 회원 한 명의 키보드 빌드. 이력은 남기지 않는다(build-design Q8). */
@Entity
@Table(name = "keyboard_build")
public class KeyboardBuild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "housing_material", length = 30)
    private String housingMaterial;

    @Column(name = "plate_material", length = 30)
    private String plateMaterial;

    @Column(name = "mount_type", length = 30)
    private String mountType;

    @Column(name = "keycap_material", length = 30)
    private String keycapMaterial;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KeyboardBuild() {}

    /** 입력값은 서비스에서 검증·정규화를 마친 뒤 넘어온다. */
    KeyboardBuild(Member member, BuildInput in) {
        this.member = member;
        apply(in);
        this.hidden = false;
        this.createdAt = Instant.now();
    }

    void apply(BuildInput in) {
        this.name = in.name();
        this.housingMaterial = in.housingMaterial();
        this.plateMaterial = in.plateMaterial();
        this.mountType = in.mountType();
        this.keycapMaterial = in.keycapMaterial();
        this.memo = in.memo();
        this.updatedAt = Instant.now();
    }

    void changeHidden(boolean hidden) {
        this.hidden = hidden;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getName() { return name; }
    public String getHousingMaterial() { return housingMaterial; }
    public String getPlateMaterial() { return plateMaterial; }
    public String getMountType() { return mountType; }
    public String getKeycapMaterial() { return keycapMaterial; }
    public String getMemo() { return memo; }
    public boolean isHidden() { return hidden; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
