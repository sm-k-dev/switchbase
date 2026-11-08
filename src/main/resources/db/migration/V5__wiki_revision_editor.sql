-- 편집자를 회원 행과 연결한다. 권한 판단(되돌리기 본인 확인)은 이 값으로 한다.
ALTER TABLE switch_revision
    ADD COLUMN editor_member_id BIGINT REFERENCES member (id) ON DELETE SET NULL;

-- 최근 변경 목록을 시간순으로 읽는다.
CREATE INDEX ix_switch_revision_created ON switch_revision (created_at DESC);
