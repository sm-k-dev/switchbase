-- 신고 대상에 타건음 샘플을 추가한다 (sound-compare-design §5.2 재생 실패 신고).
-- V9의 ck_report_target 제약을 바꾼다. V9 파일 자체는 수정하지 않는다.
ALTER TABLE report DROP CONSTRAINT ck_report_target;
ALTER TABLE report ADD CONSTRAINT ck_report_target CHECK (target_type IN ('REVIEW','RATING','BUILD','SOUND'));
