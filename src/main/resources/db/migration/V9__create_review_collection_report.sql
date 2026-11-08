-- ===== 회원: 탈퇴(익명화)와 컬렉션 공개 설정 (review-collection-report §6-4, §6-5 Q19) =====
ALTER TABLE member ADD COLUMN deleted_at TIMESTAMPTZ;          -- 탈퇴 시각. NULL = 활동 중
ALTER TABLE member ADD COLUMN collection_public BOOLEAN NOT NULL DEFAULT FALSE; -- Q8
ALTER TABLE member ALTER COLUMN email DROP NOT NULL;           -- 탈퇴 즉시 익명화(Q19)
ALTER TABLE member ALTER COLUMN password_hash DROP NOT NULL;   -- 탈퇴 즉시 익명화(Q19)

-- ===== 소프트 딜리트 컬럼 (평가·후기·빌드·신고) =====
ALTER TABLE keyboard_build ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE rating ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE rating ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;   -- 신고 처리로 숨김(Q14)

-- 평가 유일성: 삭제된 행은 제외하는 부분 유니크 (§6-4 구현 영향 2). NULLS NOT DISTINCT 유지
ALTER TABLE rating DROP CONSTRAINT uk_rating_switch_member_build;
CREATE UNIQUE INDEX uk_rating_switch_member_build
    ON rating (switch_id, member_id, build_id) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

-- ===== 후기 (스위치당 1건, Q2) =====
CREATE TABLE review (
    id               BIGSERIAL    PRIMARY KEY,
    switch_id        BIGINT       NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    member_id        BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    body             VARCHAR(2000) NOT NULL,
    recommended_for  VARCHAR(100),           -- 추천 대상 고정 코드, 쉼표 구분 (Q4)
    helpful_count    INT          NOT NULL DEFAULT 0,  -- review_vote에서 갱신하는 캐시 (§3)
    hidden           BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_review_switch_member ON review (switch_id, member_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_review_switch ON review (switch_id, created_at DESC);

CREATE TABLE review_vote (
    review_id  BIGINT NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    PRIMARY KEY (review_id, member_id)
);

-- ===== 컬렉션: 회원 × 스위치 × 상태, 각각 독립 토글 (Q7), 해제는 하드 삭제 (Q18) =====
CREATE TABLE collection_item (
    member_id  BIGINT      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    switch_id  BIGINT      NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, switch_id, status),
    CONSTRAINT ck_collection_status CHECK (status IN ('FAVORITE','OWNED','TRIED'))
);
CREATE INDEX ix_collection_switch ON collection_item (switch_id);

-- ===== 신고 (Q9~Q13). 신고자 탈퇴 시 연결만 끊는다(SET NULL, §6-4 구현 영향 4) =====
CREATE TABLE report (
    id                   BIGSERIAL    PRIMARY KEY,
    reporter_member_id   BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    target_type          VARCHAR(20)  NOT NULL,
    target_id            BIGINT       NOT NULL,
    reason_code          VARCHAR(30)  NOT NULL,
    detail               VARCHAR(500),
    status               VARCHAR(20)  NOT NULL,
    resolution           VARCHAR(20),
    handled_by_member_id BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    handled_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT ck_report_target CHECK (target_type IN ('REVIEW','RATING','BUILD')),
    CONSTRAINT ck_report_status CHECK (status IN ('RECEIVED','RESOLVED','DISMISSED')),
    CONSTRAINT uk_report_once UNIQUE (reporter_member_id, target_type, target_id)
);
CREATE INDEX ix_report_status ON report (status, created_at);

-- ===== 재가입 차단 (Q15, Q19): 탈퇴한 이메일의 해시와 만료 시각 =====
CREATE TABLE signup_block (
    email_hash  VARCHAR(64)  PRIMARY KEY,
    expires_at  TIMESTAMPTZ  NOT NULL
);

-- ===== 집계 채우기 재정의 (Q14, Q16): 삭제·숨김·탈퇴 후 30일 경과 평가는 제외 =====
DELETE FROM switch_rating_stats;
INSERT INTO switch_rating_stats (switch_id, avg_sound, avg_fatigue, avg_star, member_count, rating_count, updated_at)
SELECT r.switch_id,
       ROUND(AVG(r.sound_level), 2),
       ROUND(AVG(r.fatigue_level), 2),
       ROUND(AVG(r.star_rating), 2),
       COUNT(DISTINCT r.member_id),
       COUNT(*),
       now()
FROM rating r
JOIN member m ON m.id = r.member_id
WHERE r.deleted_at IS NULL
  AND r.hidden = FALSE
  AND (m.deleted_at IS NULL OR m.deleted_at > now() - INTERVAL '30 days')
GROUP BY r.switch_id;
