# 후기·내 컬렉션·신고 설계 (2단계, 초안)

작성: 아키텍트 / 2026-11-08 / 상태: **완전 확정** (2026-11-08, 사용자 답변 Q1~Q19 및 6-6 확인 목록 전부 해소). 구현 지시는 팀장 확인 뒤
기준: `docs/HANDOFF.md` §3 (review, collection_item, report, moderation 패키지, 원칙 "게시·삭제·되돌리기·차단은 항상 사람이 버튼"), §4 4단계(후기 요약은 AI, 4단계), 현재 구현(`ratings`, `builds`, `member`, `common/SecurityConfig`)

## 1. 목표와 범위

세 기능을 한 문서에 묶되, 데이터와 권한은 각각 따로 정한다.

| 기능 | 한 줄 설명 |
|---|---|
| 후기 (review) | 스위치에 대한 짧은 글. 추천 대상과 도움됨 수를 가진다. 평가(체감 값)와 달리 글이다. |
| 내 컬렉션 (collection_item) | 회원이 스위치를 즐겨찾기, 보유, 써봄으로 표시한다. |
| 신고 (report) | 후기·평가 등을 신고하고, 관리자가 처리한다. 처리 흐름은 사람이 버튼을 누른다. |

포함: 각 기능의 등록·수정·삭제, 권한, 목록과 상세 화면, 신고 접수와 관리자 처리, 신고 대상 정의.
제외: 후기 AI 요약(4단계 ①), 회원 탈퇴와 개인정보 처리방침(2단계 마지막 항목), 알림, 댓글.

## 2. 현재 상태

- 평가(`rating`): 스위치×빌드 조합당 1건, 수정은 작성자, 삭제는 작성자 또는 관리자. 숨김 개념 없음. 집계 캐시는 평가 저장 때 동기 갱신.
- 빌드(`keyboard_build`): `hidden` 플래그가 있고, 숨김 빌드는 작성자에게만 보인다. 공개 빌드만 신고 대상이다(§4.3).
- 회원(`member`): 로그인 이름은 이메일, 공개 이름은 닉네임. 이메일은 어떤 화면에도 나오지 않는다(0단계 규칙을 그대로 따른다).
- 스위치 상세 화면(`switches/detail`)에 평가 목록(`ratings/section`)이 include되어 있다. 후기도 같은 위치에 붙는다.

## 3. 데이터 초안: `V9__create_review_collection_report.sql` (V1~V8 수정 금지)

```sql
-- ===== 기존 테이블 변경 (V1~V8 수정 금지, V9에서만 ALTER) =====

-- 회원: 탈퇴 시각(소프트 딜리트), 익명화를 위해 이메일·해시 NULL 허용, 컬렉션 공개 설정(§6 Q8)
ALTER TABLE member ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE member ALTER COLUMN email DROP NOT NULL;
ALTER TABLE member ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE member ADD COLUMN collection_public BOOLEAN NOT NULL DEFAULT FALSE;

-- 평가: 숨김(§6 Q14, Q12)과 소프트 딜리트(§6-4)
ALTER TABLE rating ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rating ADD COLUMN deleted_at TIMESTAMPTZ;
-- 기존 테이블 제약을 부분 유니크 인덱스로 교체: 삭제된 행은 같은 조합으로 다시 쓸 수 있다
ALTER TABLE rating DROP CONSTRAINT uk_rating_switch_member_build;
CREATE UNIQUE INDEX uk_rating_active ON rating (switch_id, member_id, build_id) NULLS NOT DISTINCT
    WHERE deleted_at IS NULL;

-- 빌드: 탈퇴 시에만 소프트 딜리트(삭제 기능은 없음, §6-4)
ALTER TABLE keyboard_build ADD COLUMN deleted_at TIMESTAMPTZ;

-- ===== 신규 테이블 =====

-- 재가입 차단: 이메일 해시와 만료 시각(탈퇴일 + 30일, §6-5 Q15, Q19)
CREATE TABLE signup_block (
    email_hash    VARCHAR(64)  PRIMARY KEY,   -- 이메일 소문자 정규화 후 SHA-256 hex
    blocked_until TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_signup_block_until ON signup_block (blocked_until);

-- 후기 (글). 스위치당 1건, 수정으로 갱신(§6 Q2)
CREATE TABLE review (
    id               BIGSERIAL     PRIMARY KEY,
    switch_id        BIGINT        NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    member_id        BIGINT        NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    body             VARCHAR(2000) NOT NULL,
    recommended_for  VARCHAR(100),           -- 추천 대상 고정 코드, 쉼표 구분(§6 Q4)
    helpful_count    INT           NOT NULL DEFAULT 0,   -- review_vote 행 수로 다시 계산해 저장(§6 R2)
    hidden           BOOLEAN       NOT NULL DEFAULT FALSE, -- 신고 처리 숨김(§6 Q12, Q13)
    version          BIGINT        NOT NULL DEFAULT 0,
    deleted_at       TIMESTAMPTZ,            -- 소프트 딜리트(§6-4)
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
);
-- 스위치당 활성 후기 1건(§6 Q2). 삭제된 후기는 제외
CREATE UNIQUE INDEX uk_review_active ON review (switch_id, member_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_review_switch ON review (switch_id, created_at DESC);

-- 도움됨 투표 (회원당 1회, 토글은 행 추가/삭제). 탈퇴 시 즉시 하드 삭제(§6-4)
CREATE TABLE review_vote (
    review_id  BIGINT NOT NULL REFERENCES review (id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    PRIMARY KEY (review_id, member_id)
);

-- 컬렉션: 회원 × 스위치 × 상태, 독립 토글(§6 Q7). 해제는 하드 삭제(§6-5 Q18)
CREATE TABLE collection_item (
    member_id  BIGINT      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    switch_id  BIGINT      NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,        -- FAVORITE, OWNED, TRIED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, switch_id, status),
    CONSTRAINT ck_collection_status CHECK (status IN ('FAVORITE','OWNED','TRIED'))
);
CREATE INDEX ix_collection_switch ON collection_item (switch_id);

-- 신고 1건. 회원당 대상당 1회(§6 Q10). 신고자 탈퇴 시 연결만 끊는다(§6-4)
CREATE TABLE report (
    id                   BIGSERIAL    PRIMARY KEY,
    reporter_member_id   BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    target_type          VARCHAR(20)  NOT NULL,   -- REVIEW, RATING, BUILD (§6 Q9)
    target_id            BIGINT       NOT NULL,
    reason_code          VARCHAR(30)  NOT NULL,   -- 고정 사유 코드(§6 Q11)
    detail               VARCHAR(500),            -- 자유 메모
    status               VARCHAR(20)  NOT NULL,   -- RECEIVED, RESOLVED, DISMISSED
    resolution           VARCHAR(20),             -- HIDDEN, DISMISSED(§6 Q12)
    handled_by_member_id BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    handled_at           TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,             -- 소프트 딜리트(§6-4). 신고 기록은 기본 보존
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_target CHECK (target_type IN ('REVIEW','RATING','BUILD')),
    CONSTRAINT ck_report_status CHECK (status IN ('RECEIVED','RESOLVED','DISMISSED'))
);
-- 같은 회원이 같은 대상을 두 번 신고하지 못하게 한다. 삭제된 신고는 제외
CREATE UNIQUE INDEX uk_report_once ON report (reporter_member_id, target_type, target_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_report_status ON report (status, created_at);

-- ===== 완전 삭제 대상 확인용 인덱스(§6-5 Q17 매일 스케줄) =====
CREATE INDEX ix_member_deleted ON member (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_rating_deleted ON rating (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_review_deleted ON review (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_build_deleted ON keyboard_build (deleted_at) WHERE deleted_at IS NOT NULL;
```

- 신고 대상은 `target_type`과 `target_id`로 가리킨다. 대상 테이블에 외래키를 걸지 않는다(세 종류를 한 컬럼으로 묶기 위한 선택, 무결성은 서비스가 검사). 대상이 삭제되면 신고 행은 남아 관리자가 확인할 수 있게 한다.
- 도움됨 수는 `review_vote`에서 세서 `review.helpful_count`를 갱신한다(같은 트랜잭션). 집계 캐시와 같은 방식이다.

### 3-1. 결정 기록 (사용자, 2026-11-08, V9 대조 후속)

- **질문 1 — `uk_review_switch_member`(스위치당 후기 1건, 부분 유니크):** 유지한다. 이 인덱스는 §6 Q2의 "스위치당 1건"을 DB가 보장하는 것이며, 위 DDL에는 없지만 이 결정으로 설계에 포함된다.
- **질문 2 — 삭제 컬럼:** `review.deleted_at`은 유지한다(§6-4 소프트 딜리트). `report.deleted_at`은 쓰이지 않으므로 V10(`V10__drop_report_deleted_at.sql`)에서 제거한다. V9는 수정하지 않는다.

## 4. 기능별 설계 (질문 답에 따라 확정)

### 4.1 후기
- 등록: 스위치 상세 하단 "후기 쓰기" → 폼(본문, 추천 대상). 자격은 §6 Q1.
- 수정·삭제: 작성자 본인. 관리자 삭제 허용 여부는 §6 Q3.
- 목록: 스위치 상세 하단, 20건씩, 정렬은 §6 Q6. 각 항목은 닉네임, 본문, 추천 대상, 도움됨 수, 등록 시각을 보인다.
- 도움됨: 로그인 회원이 누르면 `review_vote`에 한 줄 추가, 다시 누르면 취소(토글). 본인 후기에는 누를 수 없다(§6 Q5).
- 숨김 후기(신고 처리): **작성자에게만 보인다.** 관리자도 볼 수 없고 해제도 작성자만 가능하다(6-2 확정, 빌드와 같음).
- 권한 검사는 서비스에서 한다.

### 4.2 내 컬렉션
- 스위치 상세에 상태 토글 버튼(즐겨찾기, 보유, 써봄). 로그인 필요.
- 내 컬렉션 화면 `/me/collection`: 상태별 탭, 스위치 이름과 상태, 최근 추가순. 본인만 본다.
- 다른 회원에게 보이는지는 §6 Q8.
- 스위치가 삭제되면 컬렉션 행도 함께 사라진다(외래키 `ON DELETE CASCADE`).

### 4.3 신고
- 신고 버튼: 후기, 평가, 빌드(§6 Q9)의 상세 또는 목록 항목에 둔다. 로그인 필요.
- **숨김 후기·평가도 신고 대상에서 제외한다(사용자 확정, 숨김 빌드와 같은 기준).** 숨김 후기·평가는 작성자에게만 보이므로 다른 회원이 신고할 수 없다. 접수 시점에 대상이 숨김이면 거부하고 레코드를 만들지 않는다. 삭제된 대상도 같은 방식으로 거부한다.
- **숨김 빌드는 신고 대상에서 제외한다(사용자 확정).** 숨김 빌드는 작성자 외에 상세가 404이므로 다른 회원이 찾아서 신고할 수 없다. Q9의 "빌드"는 공개 빌드만 뜻한다. 신고 접수 시점에 대상 빌드가 숨김이면 거부한다(화면에 버튼이 없고, 서버도 검사). 신고가 접수된 뒤 작성자가 빌드를 숨기면 기존 신고는 처리 화면에 대상 상태(숨김)로 남는다.
- 본인의 대상은 신고할 수 없다(Q10).
- 신고 폼: 사유(고정 선택지, §6 Q11) + 메모(선택).
- 접수: `report` 행을 `RECEIVED`로 저장. 중복 신고 처리는 §6 Q10.
- 관리자 목록 `/admin/reports`: 접수 순 정렬, 상태 필터(접수됨·처리됨·기각됨). 관리자 전용(`/admin/**` 규칙).
- 관리자 처리: 대상 보기, 처리 결과 선택(§6 Q12) → `RESOLVED` 또는 `DISMISSED`. 처리 기록(처리자, 시각)을 남긴다.
- 숨김 처리는 관리자가 버튼을 눌러야 하며, 접수 즉시 숨김이 되는지는 §6 Q13.
- 숨김된 평가는 집계에서 빠지는지 §6 Q14.

## 5. 권한 요약 (초안)

| 행동 | 누가 | 비고 |
|---|---|---|
| 후기 목록 보기 | 누구나 (숨김·삭제 제외) | 숨김 후기는 작성자만 |
| 후기 등록 | 로그인 회원 (Q1) | |
| 후기 수정 | 작성자 | |
| 후기 삭제 | 작성자 (관리자 Q3) | |
| 도움됨 | 로그인 회원, 본인 후기 제외 (Q5) | |
| 컬렉션 상태 변경 | 본인 | |
| 컬렉션 보기 | 본인 (공개 여부 Q8) | |
| 신고 접수 | 로그인 회원 (Q10) | 본인 대상은 불가 |
| 신고 목록·처리 | 관리자 | `/admin/**` |

## 6. 결정 사항 (사용자 답변, 2026-11-08)

**후기**

| # | 확정 |
|---|---|
| Q1 | 작성 자격: **로그인 회원 누구나** |
| Q2 | 건수: **스위치당 1건**, 수정으로 갱신 |
| Q3 | 수정·삭제: **작성자는 수정·삭제, 관리자는 삭제** |
| Q4 | 추천 대상: **고정 선택지, 여러 개 선택** |
| Q5 | 도움됨: **로그인 회원, 본인 후기 제외, 토글**(누르면 켜지고 다시 누르면 꺼짐) |
| Q6 | 정렬: **최신순 기본, 도움됨순 선택** |

**내 컬렉션**

| # | 확정 |
|---|---|
| Q7 | 즐겨찾기·보유·써봤음을 **각각 독립 토글**(한 스위치에 여러 개 가능) |
| Q8 | 공개 여부는 **전체 컬렉션을 회원이 한 번에 공개/비공개**(항목별 아님). 기본은 비공개 |

**신고**

| # | 확정 |
|---|---|
| Q9 | 대상: **후기, 평가, 빌드**. 빌드는 **공개 빌드만** 신고 대상이다. 숨김 빌드는 신고 대상에서 제외(사용자 확정, 2026-11-08). **숨김 처리된 후기·평가도 신고 대상에서 제외**(숨김 빌드와 같은 기준, 사용자 확정 2026-11-08, 리뷰 권고 반영) |
| Q10 | 자격: **로그인 회원 누구나, 본인 대상 불가**. 중복: **회원당 대상당 1회** |
| Q11 | 사유: **고정 선택지 + 자유 메모** |
| Q12 | 처리 결과: **숨김 / 기각** |
| Q13 | 접수 시: **접수만 하고 관리자 처리 때 숨김** |
| Q14 | **숨김 처리된 평가는 스위치 집계에서 제외** (§6-1 영향 범위 참고) |

## 6-1. Q14 집계 변경의 영향 범위 (기존 stats-design 규칙 변경)

현재 배포된 규칙: 집계(`switch_rating_stats`)는 `rating` 전체로 계산하고, 평가 등록·수정·삭제 때 같은 트랜잭션에서 갱신한다(stats-design §5). Q14에 따라 이 규칙이 다음과 같이 바뀐다.

1. **집계식 변경:** `RatingStatsService`의 재계산 쿼리에 `hidden = FALSE` 조건을 넣는다. 표본 수(회원 수), 건수, 평균 세 가지 모두 숨김 평가를 뺀다. V8 채우기 SQL도 같은 조건을 넣어야 한다(V8은 수정하지 않는다. 새 마이그레이션 V9에서 `rating.hidden`을 추가하고, 집계 행을 한 번 다시 계산하는 `UPDATE`를 V9에 넣는다).
2. **숨김·해제 시 갱신:** 평가를 숨기거나 숨김을 해제하는 처리 때 같은 트랜잭션에서 해당 스위치의 집계를 재계산한다(등록·수정·삭제와 같은 방식).
3. **재계산 명령:** `POST /admin/stats/recompute`(recomputeAll)는 그대로 쓰되, 숨김 평가를 제외한 결과를 낸다. 명령의 의미가 "rating 전체"에서 "공개 평가 전체"로 바뀌므로 관리자 화면 문구("평균과 평가 수를 평가 목록에서 처음부터 다시 계산")를 "공개된 평가 기준"으로 맞춘다.
4. **요약 조회:** 상세의 요약은 집계 행을 읽으므로 자동으로 숨김 평가가 빠진다. 평가 목록(`ratings/section`)에서도 숨김 평가는 일반 사용자에게 보이지 않는다(§6-2 확인 필요).
5. **테스트 변경(개발자):**
   - 기존 집계 테스트(`RatingStatsTest`)의 기대값 중 숨김 평가가 없는 경우는 그대로 통과해야 한다.
   - 추가: 숨김 평가는 표본 수·건수·평균에서 빠진다, 숨김 해제하면 다시 들어간다, 숨긴 평가가 마지막 1건이면 집계 행이 없음(0/null)과 같아진다, 재계산 명령 결과가 숨김 제외 식과 일치한다, V9 채우기 후 숨김 평가가 집계에 없다.
6. **영향 받는 기존 문서:** `stats-design.md` §4·§5의 "rating 전체 재계산"을 "공개 평가 재계산"으로 고치는 정정 줄이 필요하다(개발자가 docs 커밋에 함께 넣는다).

## 6-2. 답변에 없던 세부 사항 (확인 필요, 아키텍트 제안 표시)

답변 범위 밖이라 추측하지 않고 표시만 한다. 확정 전에 사용자 확인이 필요하다.

- **숨김 콘텐츠의 조회 범위 (해소, 사용자 확정):** 숨김된 후기·평가는 **작성자만** 볼 수 있다. 빌드와 같은 기준이다. 관리자도 숨김 내용은 볼 수 없다(빌드와 같음).
- **숨김 해제 주체 (해소, 사용자 확정):** 후기·평가의 숨김 해제는 작성자만(빌드와 같음). 관리자도 해제할 수 없다.
- **컬렉션 공개 설정 위치:** Q8을 `member.collection_public` 컬럼(V9 ALTER)으로 구현한다고 가정했다. 별도 설정 테이블보다 단순하다는 기술 선택이며 질문은 아니다.

## 6-3. 확정 후 확인이 필요한 사항 요약
- 6-2의 세 가지 세부 사항(숨김 조회 범위, 숨김 해제 주체, 컬렉션 공개 저장 위치의 기술 선택).
- 6-4 공통 정책의 해석과 구현 영향, 6-5의 Q15~Q19.

## 6-4. 공통 정책: 소프트 딜리트와 회원 탈퇴 (사용자 확정, 2026-11-08)

**확정 원문 요지**
- 모든 사용자 삭제는 소프트 딜리트(`deleted_at`)하고, 보관 30일 뒤 완전 삭제한다. 대상은 사용자가 만든 콘텐츠(평가·후기·빌드·신고 기록 등)이고, 스위치 마스터 데이터는 제외한다.
- 유일 제약은 삭제된 행을 제외하는 **부분 유니크 인덱스**로 만든다.
- 회원 탈퇴 범위는 아래 표와 같다.

**해석 (확정 전 확인 1: 30일의 의미)**
- 해석: 탈퇴한 회원의 글(평가·후기·빌드)은 **탈퇴일로부터 30일 동안 목록에 그대로 보이고**, 30일이 지나면 목록에서 빼고 완전 삭제한다. 이 30일은 소프트 딜리트 보관 기간과 같은 값이다. 즉 소프트 딜리트 행은 탈퇴 또는 삭제 후 30일 동안 살아 있고, 30일 뒤 완전 삭제 대상이 된다.
- 주의: "소프트 딜리트된 콘텐츠는 목록에서 빠진다"와 "탈퇴 회원 글은 30일 동안 목록에 보인다"가 충돌하지 않도록, 탈퇴 회원 글은 `deleted_at`이 아니라 **회원의 탈퇴 상태**로 구분해 30일 동안 노출을 유지한다(§6-4 DDL의 `member.deleted_at` 기준).

**회원 탈퇴 범위 (해석 2: 범위 목록)**

| 대상 | 탈퇴 시 처리 | 비고 |
|---|---|---|
| 평가 (rating) | 탈퇴 후 30일 유지(목록 노출 유지), 30일 뒤 목록 제외 + 완전 삭제 | 집계는 탈퇴 시점에 제외할지 §6-5 Q16 |
| 후기 (review) | 탈퇴 후 30일 유지, 30일 뒤 완전 삭제 | |
| 빌드 (keyboard_build) | 탈퇴 후 30일 유지, 30일 뒤 완전 삭제 | 빌드는 삭제 기능이 없었으므로 탈퇴 시에만 발생 |
| 컬렉션 (collection_item) | **탈퇴 시 즉시 삭제** | 소프트 딜리트 예외 |
| 도움됨 표시 (review_vote) | **탈퇴 시 즉시 삭제**, 후기의 `helpful_count`도 갱신 | 소프트 딜리트 예외 |
| 내가 한 신고 | 신고자 연결만 끊고(`reporter_member_id` NULL), 처리 기록(처리 결과·처리 시각)은 유지 | 신고 기록은 그대로 보관 |
| 내 글에 대한 신고 | 기록 유지 | 관리자 검토용 |
| 스위치 데이터 | 불변 | 마스터 데이터 |
| 위키 수정 기록의 편집자 | "탈퇴한 회원"으로 표시, 내용·시각 유지 | `switch_revision` 편집자 표시 |
| 이메일 · 비밀번호 해시 · 인증 토큰 | **탈퇴 시 즉시 삭제 또는 익명화** | 아래 "익명화" 항목 참고 |

**구현 영향 (기존 코드 변경이 필요한 곳)**
1. **평가 삭제 (기존 구현 변경):** `RatingService.delete`가 현재 행을 실제 삭제한다(`ratings.delete(r)`). 소프트 딜리트로 바꿔 `deleted_at`을 찍는다. 집계 재계산은 `deleted_at IS NULL`인 평가만 센다(Q14 `hidden`과 같은 방식). 삭제는 집계에서 빠지므로 §6-1의 집계 영향 범위에 **삭제도 추가**된다.
2. **평가 유일 제약 (기존 구현 변경):** 현재 `uk_rating_switch_member_build`는 테이블 제약이다. 부분 유니크 인덱스(`WHERE deleted_at IS NULL`)로 바꾼다. 같은 조합을 삭제한 뒤 다시 등록하는 것을 허용하기 위함이다. 기존 제약의 `NULLS NOT DISTINCT`는 인덱스에도 그대로 쓴다.
3. **회원 외래키 (기존 구현 변경):** 평가·빌드·후기 등의 `member_id`는 현재 `ON DELETE CASCADE`다. 회원을 소프트 딜리트하면 행이 남으므로 cascade는 발동하지 않는다. 대신 탈퇴 처리 서비스가 컬렉션과 도움됨을 즉시 지우고 나머지는 `deleted_at`을 찍는다. 완전 삭제 배치는 행을 지우므로 cascade가 발동한다(의도된 동작).
4. **신고 외래키:** `report.reporter_member_id`는 현재 `ON DELETE CASCADE`다. 탈퇴 시 신고자 연결을 끊어야 하므로 `ON DELETE SET NULL`로 바꾼다(또는 탈퇴 처리에서 NULL로 갱신). 처리 기록은 남는다.
5. **위키 편집자 표시:** `switch_revision.editor_member_id`는 `ON DELETE SET NULL`이고, 빈 값이면 "초기 관리자"로 보인다. 탈퇴 회원의 편집 기록은 "탈퇴한 회원"으로 보여야 하므로, 회원 행을 남기고(익명화) `getEditorDisplay`가 회원 상태를 보고 "탈퇴한 회원"을 쓰게 한다. 완전 삭제 뒤 `editor_member_id`가 NULL이 되면 "초기 관리자"로 잘못 보이므로, **편집 기록은 회원 행을 완전 삭제하지 않고 익명화 행으로 남긴다**는 규칙을 둔다(§6-5 Q15 연계).
6. **회원 행 익명화:** 탈퇴 시 `email`, `password_hash`, 인증 토큰(`email_verification_token`)은 삭제하거나 익명화한다. `email`과 `password_hash`는 현재 NOT NULL이므로 V9에서 NULL 허용으로 바꿔야 한다. 닉네임은 `uk_member_nickname` 때문에 "탈퇴회원-<id>" 같은 값으로 바꿔 유일성을 지킨다.
7. **집계:** 평가 소프트 딜리트와 숨김은 같은 방식으로 집계에서 뺀다. 재계산 명령과 V9 채우기도 `deleted_at IS NULL AND hidden = FALSE`를 쓴다.

**완전 삭제 (30일 뒤)**
- 30일이 지난 `deleted_at`(또는 탈퇴일) 행을 지우는 작업이 필요하다. 실행 방식(주기 스케줄러 또는 관리자 명령)은 §6-5 Q17로 올린다. 집계 캐시의 "주기 배치 없음" 결정(stats)은 집계에만 적용된 것이므로, 이 작업은 별도 판단이다.

## 6-5. Q15~Q19 답변 (사용자 확정, 2026-11-08)

| # | 확정 |
|---|---|
| Q15 | 탈퇴 후 **30일 동안 같은 이메일 재가입 금지**(완전 삭제 전까지) |
| Q16 | 탈퇴 회원의 평가는 **30일 뒤 집계에서 제외**(목록과 같은 기준). 즉시 제외가 아님 |
| Q17 | **매일 한 번 스케줄 작업**으로 30일 지난 행을 완전 삭제 |
| Q18 | 컬렉션 해제는 **하드 삭제** |
| Q19 | 이메일·비밀번호 해시는 **탈퇴 즉시 NULL로 익명화**하고 회원 행은 남김. 재가입 차단용 이메일 해시는 **별도 테이블**(탈퇴일 + 30일 만료)에 둔다(사용자 확정, 6-6 (1)) |

**Q16 적용 메모:** 집계 제외 기준이 "탈퇴 후 30일 경과"이므로, 집계 재계산 조건에 `member.deleted_at`이 30일 이전인 평가를 빼는 조건이 들어간다. 스케줄 작업이 완전 삭제할 때 해당 스위치 집계를 함께 다시 계산한다(평가 행이 cascade로 사라지므로 집계가 어긋나지 않게).

**Q15·Q19 시점 충돌 (확인 필요):** Q19는 익명화(이메일 NULL)를 말하지만 시점을 정하지 않았다. Q15는 이메일로 30일 동안 재가입을 막아야 한다. 탈퇴 즉시 이메일을 NULL로 바꾸면 재가입 금지를 걸 근거가 없다. 두 답을 함께 지키려면 아래 중 하나가 필요하다.
- (a) 탈퇴 즉시 이메일 NULL, 재가입 금지는 **이메일 해시를 별도 테이블**(`signup_block`, 탈퇴일 + 30일 만료)에 두어 구현 — *추천*. 회원 행에는 개인 식별자가 남지 않는다.
- (b) 익명화를 30일 뒤(완전 삭제 시점)로 미룸. 30일 동안 회원 행에 이메일이 남는다.

## 6-6. 확정 전 확인 목록 (해소 여부)
1. **Q15와 Q19의 익명화 시점 충돌: 해소.** 익명화는 탈퇴 즉시(이메일·해시 NULL), 재가입 차단은 별도 테이블(이메일 해시, 탈퇴일 + 30일 만료). 사용자 확정.
2. **30일의 의미와 범위 목록(6-4): 해소.** 이전 보고 그대로 유지. 탈퇴 후 30일 동안 평가·후기·빌드 목록 노출 유지, 30일 뒤 목록 제외와 완전 삭제. 컬렉션·도움됨은 즉시 삭제. 범위 표 유지.
3. **기존 구현 변경 5건과 집계 영향: 해소.** 평가 삭제 소프트화, 평가 부분 유니크(`deleted_at IS NULL`), 회원 외래키(행 유지, 탈퇴 처리가 컬렉션·도움됨 삭제), 신고 외래키(`SET NULL`), 위키 편집자 표시("탈퇴한 회원", 익명화 행 유지). 집계 제외 조건은 `deleted_at`, `hidden`, 탈퇴 후 30일 경과 세 가지.
4. **숨김 콘텐츠 조회 범위: 해소**(작성자만, 빌드와 같음). **숨김 해제 주체: 해소**(사용자 확정: 후기·평가의 숨김 해제는 작성자만, 빌드와 같은 기준. 관리자는 해제할 수 없다).

## 7. 작업 분담 (확정, 팀장 확인 후 지시)

1. **개발자**
   - V9: 소프트 딜리트 컬럼(`deleted_at`: 평가·후기·빌드·신고, 회원), `rating.hidden`, `member.collection_public`, 부분 유니크(평가 `WHERE deleted_at IS NULL`), 회원 이메일·해시 NULL 허용, 재가입 차단 테이블(`signup_block`: 이메일 해시, 만료 시각), 외래키 변경(신고 `SET NULL`), 기존 집계 재계산 조건(`deleted_at IS NULL AND hidden = FALSE`, 탈퇴 후 30일 경과 제외) 반영.
   - 평가 삭제 소프트화, 탈퇴 처리 서비스(회원 익명화 즉시, 이메일 해시를 차단 테이블에, 컬렉션·도움됨 하드 삭제, 나머지는 소프트 딜리트, 신고자 연결 끊기), 편집자 표시("탈퇴한 회원").
   - 후기 CRUD와 도움됨 토글, 컬렉션 토글(하드 삭제), 신고 접수와 관리자 처리(숨김·기각), 숨김 콘텐츠는 작성자만 조회.
   - 매일 한 번 스케줄 작업: 30일 지난 행 완전 삭제, 영향 받은 스위치 집계 재계산.
   - 재가입 차단 확인(회원가입 시 이메일 해시가 차단 테이블에 있고 만료 전이면 거부).
   - 테스트, `stats-design.md` 정정 줄.
   - 테스트 실행은 개발자 한 명만 `sg docker -c "./gradlew test"`.
2. **디자이너**: 후기 목록·폼·도움됨, 컬렉션 토글과 `/me/collection`(공개 설정), 신고 버튼·폼, `/admin/reports`, 탈퇴 화면(안내와 확인), 탈퇴 회원 편집 기록 표시, 숨김 상태 표시(작성자에게만).
3. **리뷰어**: 탈퇴 범위 누출(이메일, 해시), 부분 유니크, 신고 기록 보존과 연결 끊기, 재가입 차단, 집계 제외 조건 세 가지, 스케줄 작업의 멱등성.

**확정 사항 반영:** 숨김 해제는 후기·평가 모두 작성자만 가능하다(관리자 포함 다른 회원은 404, 빌드 기준과 같음). 해제 기능을 구현 범위에 포함한다.

## 8. 열린 위험

- 신고 대상이 세 종류라서 `target_id` 무결성을 서비스가 지킨다. 대상이 사라지면 관리자 화면에 "대상 없음"으로 보이게 한다.
- 관리자 처리가 늦으면 악성 후기가 오래 남는다(Q13 (a)의 대가). 관리자 목록에 접수 시각을 보여 준다.
- 도움됨 수 캐시는 토글이 동시에 들어오면 어긋날 수 있다. 같은 트랜잭션에서 `review_vote`와 `helpful_count`를 함께 갱신하고, 재계산 명령을 집계와 같은 방식으로 둘지 정한다(추가 질문 없이 집계 항목과 같은 방식으로 제안).
