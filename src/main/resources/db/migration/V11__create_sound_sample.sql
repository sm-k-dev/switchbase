-- ===== 타건음 샘플 (sound-compare-design §3) =====
-- 음원 파일은 저장하지 않는다(저작권). 유튜브 영상 id와 시작 시간만 둔다.
CREATE TABLE sound_sample (
    id              BIGSERIAL     PRIMARY KEY,
    switch_id       BIGINT        NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    member_id       BIGINT        REFERENCES member (id) ON DELETE SET NULL,
    youtube_id      VARCHAR(11)   NOT NULL,
    start_seconds   INT           NOT NULL DEFAULT 0,
    channel_name    VARCHAR(100)  NOT NULL,
    hidden          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT ck_sound_start CHECK (start_seconds >= 0),
    CONSTRAINT ck_sound_youtube_id CHECK (youtube_id ~ '^[A-Za-z0-9_-]{11}$')
);

-- 같은 스위치에 같은 영상과 시작 시간은 한 번만 (삭제된 행 제외)
CREATE UNIQUE INDEX uk_sound_sample_active
    ON sound_sample (switch_id, youtube_id, start_seconds) WHERE deleted_at IS NULL;
CREATE INDEX ix_sound_sample_switch_channel ON sound_sample (switch_id, channel_name);

-- 녹음 조건 (고정 선택지. 코드값은 SoundCondition enum과 맞춘다)
CREATE TABLE sound_sample_condition (
    sound_sample_id  BIGINT       NOT NULL REFERENCES sound_sample (id) ON DELETE CASCADE,
    condition_code   VARCHAR(30)  NOT NULL,
    PRIMARY KEY (sound_sample_id, condition_code)
);
