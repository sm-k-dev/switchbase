CREATE TABLE member (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,          -- 저장 시 소문자·trim 정규화
    password_hash   VARCHAR(100) NOT NULL,          -- {bcrypt}... 델리게이팅 형식
    nickname        VARCHAR(20)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,          -- 'USER' | 'ADMIN'
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_email    UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname),
    CONSTRAINT ck_member_role CHECK (role IN ('USER','ADMIN'))
);

CREATE TABLE email_verification_token (
    id          BIGSERIAL    PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,              -- SHA-256 hex. 원문은 저장하지 않음
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    CONSTRAINT uk_verification_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_verification_token_member ON email_verification_token (member_id);
