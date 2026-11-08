CREATE TABLE switch_rating_stats (
    switch_id       BIGINT       PRIMARY KEY REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    avg_sound       NUMERIC(4,2),   -- 소리 크기 평균(평가 건 기준). 평가가 없으면 NULL
    avg_fatigue     NUMERIC(4,2),   -- 피로도 평균
    avg_star        NUMERIC(4,2),   -- 별점 평균
    member_count    INT          NOT NULL DEFAULT 0,  -- 표본 수 = 서로 다른 회원 수
    rating_count    INT          NOT NULL DEFAULT 0,  -- 평가 건수 (평균의 기준)
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 기존 평가로 한 번 채운다 (stats-design Q7). 이후 갱신은 평가 저장·수정·삭제 때 같은 트랜잭션에서 한다.
INSERT INTO switch_rating_stats (switch_id, avg_sound, avg_fatigue, avg_star, member_count, rating_count, updated_at)
SELECT switch_id,
       ROUND(AVG(sound_level), 2),
       ROUND(AVG(fatigue_level), 2),
       ROUND(AVG(star_rating), 2),
       COUNT(DISTINCT member_id),
       COUNT(*),
       now()
FROM rating
GROUP BY switch_id;
