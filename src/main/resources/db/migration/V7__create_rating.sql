CREATE TABLE rating (
    id                BIGSERIAL    PRIMARY KEY,
    switch_id         BIGINT       NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    member_id         BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    build_id          BIGINT       REFERENCES keyboard_build (id) ON DELETE SET NULL, -- NULL = 빌드 없이 평가 (Q3)
    sound_level       SMALLINT     NOT NULL,   -- 소리 크기 1~5 (Q7)
    sound_tone        VARCHAR(20)  NOT NULL,   -- CLACKY(도각), POPPY(쪅), HEAVY(묵직), DULL(둔탁)
    feel              VARCHAR(20)  NOT NULL,   -- SMOOTH(부드러움), CRISP(명확함), CATCHY(걸림)
    fatigue_level     SMALLINT     NOT NULL,   -- 피로도 1~5 (Q7)
    star_rating       SMALLINT     NOT NULL,   -- 별점 1~5 (Q7 척도 5점, 필수 — 추천안 적용)
    lubed             BOOLEAN,                 -- 윤활 여부 예/아니오 (Q8, 선택 — 추천안 적용)
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_rating_switch_member_build UNIQUE NULLS NOT DISTINCT (switch_id, member_id, build_id), -- Q2
    CONSTRAINT ck_rating_sound_level CHECK (sound_level BETWEEN 1 AND 5),
    CONSTRAINT ck_rating_fatigue_level CHECK (fatigue_level BETWEEN 1 AND 5),
    CONSTRAINT ck_rating_star CHECK (star_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_rating_sound_tone CHECK (sound_tone IN ('CLACKY','POPPY','HEAVY','DULL')),
    CONSTRAINT ck_rating_feel CHECK (feel IN ('SMOOTH','CRISP','CATCHY'))
);
CREATE INDEX ix_rating_switch ON rating (switch_id);
CREATE INDEX ix_rating_member ON rating (member_id);

CREATE TABLE mod_tag (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,   -- 처음 입력한 표시 형태
    normalized  VARCHAR(50)  NOT NULL,   -- 공백 정리 + 소문자 키 (Q5 중복 방지)
    CONSTRAINT uk_mod_tag_normalized UNIQUE (normalized)
);

CREATE TABLE rating_mod_tag (
    rating_id   BIGINT NOT NULL REFERENCES rating (id) ON DELETE CASCADE,
    mod_tag_id  BIGINT NOT NULL REFERENCES mod_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (rating_id, mod_tag_id)
);
CREATE INDEX ix_rating_mod_tag_tag ON rating_mod_tag (mod_tag_id);
