# 체감 평가 + 개조 태그 설계 (2단계)

작성: 아키텍트 / 2026-11-08 / 상태: **확정** (2026-11-08, 사용자 답변 Q1~Q8 반영. Q7 필수 여부는 추천안 적용)
기준: `docs/HANDOFF.md` §3(rating, mod_tag/rating_mod_tag, 체감 항목, 감각 용어, 표본 수), §4 2단계 "체감 평가 + 개조 태그(자동완성)"

## 1. 목표와 범위

포함
- 로그인 회원이 스위치 1종에 대한 체감 평가를 등록하고 수정한다. 삭제는 작성자와 관리자 모두(§4).
- 평가에 본인 빌드를 붙이거나, 빌드 없이 평가한다.
- 스위치 상세에 평가 목록과 요약(체감 평균, **표본 수**)을 보인다.
- 개조 태그 자동완성(기존 태그 제안, 없으면 새 태그 생성).

제외 (별도 항목)
- 집계 캐시 `switch_rating_stats`와 평가 저장 시 이벤트 갱신 → "집계" 항목. 이번 항목의 요약은 **실시간 조회**로 계산한다(§5.3).
- 후기(`review`)와 컬렉션(`collection_item`) → 다음 항목.
- 감각 용어 사전 본문과 입문자 가이드 → 3단계(HANDOFF §4). 이번 화면에는 항목 이름 옆에 한 줄 설명만 둔다.
- 빌드 조건 필터로 평균 좁히기 → 3단계.

## 2. 핵심 원칙 (HANDOFF §3)

- 체감은 **각자의 의견**이다. 평가는 모아서 집계하고, 개별 평가가 스펙을 바꾸지 않는다.
- 빌드 환경은 스위치가 아니라 **평가 한 건에 붙는다**. 빌드가 바뀌면 평가 행만 늘고 열은 그대로다.
- 체감 항목은 고정 선택지 또는 척도, 개조는 자유 태그(자동완성).
- 표본 수는 항상 평균 옆에 보인다.
- 이메일은 어떤 화면에도 나오지 않는다(0단계 규칙). 작성자는 닉네임만 표시한다.

## 3. 데이터: `V7__create_rating.sql` (V1~V6 수정 금지)

```sql
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
```

- 체감 4항목과 별점은 **필수**(NOT NULL), 윤활 여부와 개조 태그는 **선택**이다. Q7 필수 여부는 추천안을 적용했다(**추천안 적용**, 사용자 미질문).
- 유일성은 `(switch_id, member_id, build_id)`에 `NULLS NOT DISTINCT`를 써서, 빌드 없는 평가도 같은 스위치에 한 건만 남게 한다(Q2).
- 소리 성향·타건감 코드와 표시 이름은 분리한다.
- 평가에 붙은 빌드가 나중에 **숨김**이 되어도 `build_id`는 남는다. 화면에는 빌드 이름을 쓰지 않는다(Q6).

## 4. 권한과 규칙 (확정)

| 행동 | 누가 | 조건 |
|---|---|---|
| 평가 목록·요약 보기 | 누구나 (비로그인 포함) | 둘러보기는 로그인 없이 |
| 평가 등록 | **로그인 회원 누구나** (Q1) | (스위치, 빌드) 조합당 1건 (Q2). 이미 있으면 수정 화면으로 안내 |
| 평가 수정 | **작성자 본인만** (Q4) | 낙관적 잠금 `version` |
| 평가 삭제 | **작성자 본인과 관리자** (Q4, 사용자 확정) | 삭제는 행을 지운다(되돌리기 없음) |
| 빌드 연결 | 작성자 본인의 빌드만 | 빌드 없이도 가능 (Q3). 숨김 빌드 연결 여부는 §6 |
| 개조 태그 생성 | **평가를 등록하는 로그인 회원 누구나** (Q5) | 정규화 키가 같으면 기존 태그를 쓴다 |

- 권한은 서비스에서 검사한다(화면 숨김에 기대지 않음).
- 평가는 스펙(`keyboard_switch`, `switch_revision`)을 바꾸지 않는다.

## 5. 화면과 동작 (확정)

### 5.1 평가 등록·수정
- 경로: GET `/switches/{id}/ratings/new`, POST `/switches/{id}/ratings`, GET `/ratings/{id}/edit`, POST `/ratings/{id}`, POST `/ratings/{id}/delete`(관리자만).
- 입력: 빌드 선택(본인 빌드 목록 + "빌드 없음"), 체감 4항목(각 1~5 또는 선택지), 별점(1~5), 윤활 여부(예/아니오/미입력), 개조 태그(여러 개).
- 항목 이름 옆에 한 줄 설명. 용어 사전 본문은 3단계.

### 5.2 개조 태그 자동완성
- 입력 칸에 글자를 치면 htmx로 `GET /mod-tags/suggest?q=`가 **HTML 조각**(후보 목록)을 돌려준다.
- 후보 선택 또는 Enter로 추가. 후보에 없으면 새 태그로 생성된다(Q5).
- 정규화: 앞뒤 공백 제거, 연속 공백 한 칸, 소문자 키. 표시 이름은 처음 입력한 형태를 쓴다.
- 자동완성 응답의 태그 이름은 반드시 이스케이프한다.

### 5.3 요약 (실시간 조회, 캐시 아님)
- 스위치 상세에 항목별 평균과 "평가 N명 · M건"을 보인다. N은 **표본 수(회원 수)**, M은 평가 건수이며 평균 옆에 항상 표시한다.
- 표본 수 = **같은 스위치를 평가한 서로 다른 회원 수**. 같은 회원이 빌드별로 여러 번 평가해도 한 명이다.
- 평균은 **평가 건(행) 기준**으로 계산하고 소수 한 자리로 표시한다. 같은 회원이 빌드별로 여러 번 평가하면 그 건수만큼 평균에 들어간다. 표본 수와 기준이 다르므로 M건을 함께 표시해 혼동을 막는다(리뷰 권고 정합 결정).
- 데이터가 커지면 집계 항목에서 캐시로 바꾼다.

### 5.4 평가 목록
- 스위치 상세 하단: 최신순 20건씩. 항목: 작성자 닉네임, 별점, 체감 4항목, 윤활 여부(있을 때), 개조 태그, 등록 시각.
- 빌드는 연결되어 있으면 **"빌드 있음"**으로만 표시한다. 빌드 이름은 쓰지 않는다(Q6). 숨김 빌드의 이름은 어디에도 나오지 않는다.
- 수정·삭제 버튼은 작성자 본인에게 보인다. 관리자에게도 다른 회원의 평가에 삭제 버튼이 보인다.

## 6. 확정 사항과 확인 요청

| # | 확정 |
|---|---|
| Q1 | 평가 자격: **로그인 회원 누구나** |
| Q2 | **스위치 × 빌드 조합당 1건**, 수정으로 갱신. 빌드 없는 평가도 스위치당 1건 |
| Q3 | 빌드 없는 평가 **허용** |
| Q4 | 수정은 **작성자만**, 삭제는 **작성자와 관리자 모두** (사용자 확정) |
| Q5 | 개조 태그는 **평가를 등록하는 회원 누구나** 생성, 정규화 키로 중복 방지 |
| Q6 | 닉네임·별점·체감 공개. 빌드는 **"빌드 있음"**만 표시, 숨김 빌드 이름 비노출 |
| Q7 | 척도 **5점**. 필수 여부는 **추천안 적용**: 체감 4항목과 별점 필수, 개조 태그와 윤활 여부 선택 |
| Q8 | 윤활은 **예/아니오** |

**확인 요청 (팀장에게 전달)**
- (해결됨) 처음 확정 문장("수정은 작성자, 삭제는 관리자")은 작성자의 삭제를 막았다. 사용자 재확정으로 작성자와 관리자 모두 삭제할 수 있게 바꿨다.
- 표본 수 정의(§5.3, 서로 다른 회원 수)와 평균의 행 기준은 설계 판단으로 정했다. 평균과 표본 수의 기준이 다르므로 화면에 건수를 함께 표시한다.

## 7. 작업 분담 (확정)

1. **개발자**
   - V7, `Rating`·`ModTag` 엔티티와 리포지토리(`ddl-auto: validate` 통과).
   - 서비스: 조합 유일성(빌드 없는 평가 포함), 수정 권한(작성자), 삭제 권한(작성자 또는 관리자), 낙관적 잠금, 태그 정규화와 생성, 요약 쿼리(표본 수 = 서로 다른 회원 수), 빌드 연결은 본인 빌드만.
   - 컨트롤러: 평가 등록·수정·삭제, 자동완성 조각(`GET /mod-tags/suggest`).
   - `SecurityConfig`: 목록·요약은 permitAll, 등록·수정·삭제·자동완성 조각은 로그인 필요(수정·삭제의 작성자·관리자 검사는 서비스에서).
   - 테스트 §8.
2. **디자이너**: 평가 입력 폼(빌드 선택, 체감 4항목 1~5 척도, 별점, 윤활 예/아니오, 태그 입력 자동완성), 스위치 상세의 요약과 평가 목록, 항목 이름 옆 한 줄 설명. "빌드 있음" 표시. 이메일 표시 금지. 삭제 버튼은 작성자와 관리자에게 보인다.
3. **리뷰어**: 권한 서버 검사(수정 작성자, 삭제 작성자 또는 관리자), 조합 유일성과 빌드 소유 검사(남의 빌드 연결 차단), 숨김 빌드 이름 비노출, 표본 수 정확성, 태그 정규화와 중복, 자동완성 응답 이스케이프.
4. **테스트 실행**: 개발자 한 명만 `sg docker -c "./gradlew test"`.

## 8. 테스트 목록 (개발자)

- 로그인 회원 평가 등록 → 목록에 닉네임·값 표시, 이메일 없음.
- 비로그인 등록 → 로그인 화면.
- 같은 (스위치, 빌드) 중복 등록 → 거부하고 수정 화면 안내. 빌드 없는 평가도 스위치당 1건.
- 남의 빌드 연결 → 거부.
- 작성자 수정 → 성공, 낙관적 잠금 충돌은 409 재렌더. 다른 회원 수정 → 403(리뷰 정합 결정, 화면 계약과 일치).
- 작성자 삭제 → 성공. 관리자의 다른 회원 평가 삭제 → 성공. 제3자 삭제 → 403.
- 표본 수: 같은 회원이 빌드 두 대로 평가하면 1명.
- 요약 평균: 척도 값의 평균이 소수 한 자리로 나온다.
- 필수 항목(체감 4항목, 별점) 누락 → 거부. 윤활·태그 누락 → 허용.
- 태그 정규화: "Spring  Swap"과 "spring swap"은 같은 태그. 자동완성 응답에서 태그 이름이 이스케이프된다.
- 숨김 빌드가 연결된 평가 → 목록에 "빌드 있음"만, 빌드 이름 없음.
- 응답 본문에 이메일 없음.

## 9. 열린 위험

- 실시간 요약은 평가가 많아지면 느려진다. 집계 항목에서 캐시로 바꾼다.
- 태그 자유 생성은 오타와 중복을 만든다. 정규화로 막고, 병합은 관리 기능으로 나중에 한다.
- 척도를 나중에 바꾸면 기존 평가와 어긋난다. 척도 값은 저장 값과 표시를 분리한다.
