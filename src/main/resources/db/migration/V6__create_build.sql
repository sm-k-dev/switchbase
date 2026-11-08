CREATE TABLE keyboard_build (
    id               BIGSERIAL    PRIMARY KEY,
    member_id        BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    name             VARCHAR(50),                        -- 선택. 비우면 화면에 "내 키보드"로 표시 (Q7)
    housing_material VARCHAR(30),                        -- 고정 선택지 코드, NULL = 미지정
    plate_material   VARCHAR(30),                        -- 보강판 고정 선택지 코드, NULL = 미지정
    mount_type       VARCHAR(30),                        -- 마운트 고정 선택지 코드, NULL = 미지정
    keycap_material  VARCHAR(30),                        -- 키캡 고정 선택지 코드, NULL = 미지정
    memo             VARCHAR(500),                       -- 자유 텍스트 (Q7). "기타" 설명도 여기에 적는다 (Q6)
    hidden           BOOLEAN      NOT NULL DEFAULT FALSE, -- 숨김. 데이터는 유지 (Q4)
    version          BIGINT       NOT NULL DEFAULT 0,    -- 낙관적 잠금
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_keyboard_build_member ON keyboard_build (member_id);
CREATE INDEX ix_keyboard_build_visible ON keyboard_build (hidden, created_at DESC);
