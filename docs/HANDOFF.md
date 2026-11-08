# SwitchBase 인계 문서

claude.ai 프로젝트 채팅방의 「SwitchBase 기획서」와 「SwitchBase 개발 계획」(둘 다 2026-10-12 작성)을 요약하고,
2026-11-08 기준 저장소 진행 상황을 붙인 문서입니다. 설계 판단이 필요하면 이 문서를 기준으로 하고,
여기 없는 결정은 추측하지 말고 사용자(사람)에게 물어봅니다.

## 1. 한 줄 소개와 원칙

- SwitchBase: 기계식 키보드 스위치를 데이터와 취향으로 정리하는 지식 + 커뮤니티 플랫폼.
  부제 "Mechanical Keyboard Switches, structured by data and taste."
- 장기 취미 프로젝트. 일정·완성도 압박보다 오래 즐겁게 손볼 수 있는 구조가 우선. 기간은 정하지 않는다.
- **프로젝트 원칙: 모든 결정과 판단은 사람이 하고, AI는 활용할 뿐이다.**
  AI는 요약·검색·제안·표시만 하고, 게시·삭제·되돌리기·차단은 항상 사람이 버튼을 누른다.
- 언어는 한국어 먼저. 스위치 이름과 제조사는 영문 원어 그대로.
- 무료 티어 위주, 월 비용 상한을 둔다(금액 미정).

## 2. 기술 스택 (확정)

| 영역 | 선택 |
|---|---|
| 언어 / 프레임워크 | Java 25, Spring Boot 4.x |
| 빌드 | Gradle (Kotlin DSL) |
| 화면 | Thymeleaf + htmx (확정. React는 나중에 검토) |
| DB | PostgreSQL 17 (+ 4단계에 pgvector) |
| 데이터 접근 | Spring Data JPA + QueryDSL (필터가 많아 동적 쿼리) |
| 마이그레이션 | Flyway (JPA `ddl-auto: validate`, 테이블은 Flyway만 만든다) |
| 인증 | Spring Security, 이메일 + 비밀번호 + 이메일 인증. SNS 로그인은 나중(OAuth2) |
| AI (4단계) | Spring AI 2.0 + Claude (Haiku 5.5: 요약·검색 변환, Sonnet 5.5: 검토 보조) |
| 테스트 | JUnit 5, Testcontainers (PostgreSQL) |
| 배포 | AWS (EC2에 jar + RDS PostgreSQL), GitHub Actions. Docker는 로컬 DB와 테스트에만 |

## 3. 핵심 설계 결정

- **스펙은 하나의 정답, 체감은 각자의 의견.** 스펙은 위키처럼 모두가 고치는 문서, 체감은 평가를 모아 집계.
- **위키 버전은 Hibernate Envers 대신 `switch_revision`을 직접 둔다.** 버전 비교·되돌리기·검토 대기 화면을 만들려면
  버전이 도메인 데이터여야 하기 때문.
- 스펙 항목마다 출처(제조사 페이지, 실측 영상)를 단다. 제조사값과 실측값이 다르면 둘 다 적는다.
- 새 스위치 등록 전 이름 검색으로 중복을 막는다.
- **빌드 환경은 스위치가 아니라 평가 한 건에 붙는다**(평가가 쌓여도 행만 늘고 열은 그대로).
  빌드 없이도 평가 가능. 핵심 항목(하우징 재질, 보강판, 마운트, 윤활)은 고정 선택지, 나머지 개조는 자동완성 자유 태그.
- 체감 평균 옆에는 항상 표본 수(평가 N명)를 표시한다.
- 타건음은 파일을 올리지 않고 **유튜브 임베드 + 시작 시간**으로만 단다(저작권). 비교 단위는 "같은 채널".
- 둘러보기는 로그인 없이. 즐겨찾기·평가·후기 작성 때만 로그인.
- 기능별 패키지: `switches(현재 keyswitch) / ratings / builds / sounds / reviews / collections / members / moderation / ai / common`

### 데이터 모델 (계획)

| 테이블 | 한 행 | 주요 컬럼 |
|---|---|---|
| switch | 스위치 1종의 현재 버전 | 제조사, 모델명, 타입, 규격, 압력·거리, 재질, 판매 상태, current_revision_id |
| switch_revision | 위키 수정 1회 | switch_id, 수정자, 스펙 스냅샷(JSON), 수정 요약, 상태(게시/검토 대기/되돌림) |
| spec_source | 스펙 항목의 출처 | revision_id, 항목명, URL, 제조사값/실측값 |
| build | 유저 키보드 1대 | member_id, 하우징 재질, 보강판, 마운트, 키캡 |
| rating | 스위치 1종 × 빌드 1대 평가 | switch_id, member_id, build_id(선택), 소리 크기, 소리 성향, 타건감, 피로도, 별점, 윤활 여부 |
| mod_tag / rating_mod_tag | 개조 태그와 평가 연결 | 태그명, 별칭 |
| switch_rating_stats | 스위치별 집계 캐시 | 평균값, 표본 수, 갱신 시각 |
| sound_sample | 타건음 영상 1개 | switch_id, 유튜브 ID, 시작 시간, 채널, 녹음 조건 태그 |
| review | 짧은 후기 | switch_id, member_id, 본문, 추천 대상, 도움됨 수 |
| collection_item | 즐겨찾기/보유/써봄 | member_id, switch_id, 상태 |
| member | 회원 | 이메일, 닉네임, 인증 여부, 역할 |
| report | 신고 1건 | 대상 종류/ID, 사유, 처리 상태 |

체감 항목: 소리 크기(조용함~큼 척도), 소리 성향(도각/쪅/묵직/둔탁 선택), 타건감(부드러움/명확함/걸림 선택), 피로도(낮음~높음 척도).
감각 용어는 뜻을 정의한 용어 사전을 두고 평가 화면에서 바로 볼 수 있게 한다.

## 4. 단계별 할 일과 현재 진행 상황

### 0단계. 뼈대 — 완료
- [x] Spring Boot 프로젝트, GitHub 저장소 (sm-k-dev/switchbase)
- [x] Docker Compose 로컬 PostgreSQL 17 (포트 5432, 앱 포트 8090)
- [x] Flyway 첫 마이그레이션, Testcontainers 테스트
- [x] GitHub Actions: 빌드 + 테스트
- [x] 공통 레이아웃(Thymeleaf), 에러 페이지

### 1단계. 나만의 스위치 위키 — 거의 완료
- [x] 스위치·revision·spec_source 엔티티 (V1~V3 마이그레이션, 브랜드·별칭 포함)
- [x] 스위치 목록 / 상세 페이지
- [x] 검색과 필터 (QueryDSL), 브랜드·변형·별칭 검색
- [x] 관리자 로그인과 등록/수정 화면 (수정은 revision으로 저장)
- [x] CSV 가져오기 + 초기 스위치 데이터 (`data/switches.csv`)
- [ ] 첫 배포 (AWS EC2 + RDS) — README에 배포 정보 커밋이 있으나 실제 배포 여부는 사용자 확인 필요

### 2단계. 커뮤니티 열기 — 시작 단계 (다음 작업 대상)
- [x] **회원가입, 이메일 인증, 로그인 (Spring Security)** — 완료 (2026-11-08, 커밋 1b2deca·e1aed40·064ce99)
  - 회원(member) 기반 인증, ADMIN/USER, `switchbase.admin.*`로 첫 관리자 생성, 이메일 인증(개발 중엔 링크를 로그 출력), 실제 로그인 흐름 통합 테스트 42개 통과.
  - 이전 커밋 eb8c0c2의 메시지는 내용과 맞지 않았다(히스토리는 그대로 둠). 상세: `docs/work/member-design.md`, `docs/work/member-progress.md`.
  - prod 프로필은 SMTP 구현이 생기기 전까지 기동 실패(의도). §5의 SMTP 서비스 선택 참고.
- [ ] 유저 위키 수정, 버전 비교, 되돌리기, 최근 변경 목록
- [ ] 빌드 등록, 체감 평가 + 개조 태그(자동완성)
- [ ] 집계: 평가 저장 시 이벤트로 switch_rating_stats 갱신, 표본 수 표시
- [ ] 후기, 내 컬렉션, 신고
- [ ] 개인정보 처리방침, 회원 탈퇴

### 3단계. 소리와 비교
- [ ] 타건음 샘플 등록 (유튜브 URL + 시작 시간 + 녹음 조건)
- [ ] A vs B 비교 화면 (스펙, 체감 평균 레이더 차트, 같은 채널 샘플 우선)
- [ ] 입문자 가이드 + 감각 용어 사전
- [ ] 빌드 조건 필터로 체감 평균 좁혀 보기

### 4단계. AI (1~3단계 데이터가 쌓인 뒤)
우선순위: ① 후기 요약(배치·캐시) ② 자연어 검색(LLM → 필터 record → 기존 QueryDSL 재사용, LLM이 SQL을 만들지 않음)
③ 비슷한 느낌의 스위치(pgvector) ④ 취향 기반 추천 ⑤ 위키 수정 검토 보조("의심" 표시만) ⑥ 녹음 조건 태그 제안.
원칙: AI가 죽어도 사이트는 돈다(타임아웃·폴백), AI 결과에 표시, 월 비용 상한, API 키는 환경 변수로만.

### 선택: NEXORA SentinelOps 연동 (2단계 이후)
SwitchBase의 오류·보안 이벤트를 NEXORA SentinelOps `POST /api/v1/events`로 비동기 전송.
`switchbase.sentinel.enabled=false`가 기본값. 자동 차단은 하지 않는다. 필수 기능 아님.

## 5. 아직 정하지 않은 것 (사람이 결정)
- 배포 위치 세부와 월 비용 상한
- 도메인 이름
- 저장소 공개 여부 (포트폴리오용이면 공개 추천)
- SMTP 발송 서비스 선택 (배포 전 결정, 현재 prod 프로필은 SMTP 구현 없으면 기동 실패)

## 6. 포트폴리오로 남길 것
설계 결정 기록(ADR: 위키 버전 직접 구현, 빌드를 평가에 붙인 이유, LLM에게 SQL을 맡기지 않은 이유),
README(소개·스크린샷·ERD·실행·배포 주소), CI에서 도는 Testcontainers 테스트.
