-- 사용자 결정(2026-11-08): report.deleted_at은 쓰이지 않으므로 제거한다. V9는 수정하지 않는다.
ALTER TABLE report DROP COLUMN deleted_at;
