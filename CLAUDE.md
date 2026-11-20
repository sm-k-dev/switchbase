# SwitchBase 프로젝트 규칙

기계식 키보드 스위치 위키 + 커뮤니티. 장기 취미 프로젝트이며 기간을 정하지 않는다.

## 원칙 (바꾸지 않는다)

- **모든 결정과 판단은 사람이 한다. AI는 요약·검색·제안·표시만 한다.** 게시·삭제·되돌리기·차단은 항상 사람이 버튼을 누른다.
- 설계에 없는 결정은 추측하지 말고 사용자에게 묻는다. 추천안을 붙여 묻는다.
- 언어는 한국어 먼저. 스위치 이름과 제조사는 영문 원어 그대로.
- 이메일은 어떤 화면에도 노출하지 않는다. 작성자는 닉네임으로만 표시한다.

## 기술 스택

- Java 25, Spring Boot 4.x, Gradle (Kotlin DSL)
- Thymeleaf + htmx, PostgreSQL 17, Flyway, Spring Data JPA + QueryDSL
- 테스트: JUnit 5, Testcontainers (Docker 필요)

## 명령

- 빌드와 테스트 (Windows PowerShell): `.\gradlew.bat test`. Docker Desktop이 켜져 있어야 한다.
- 빌드와 테스트 (Linux): `sg docker -c "./gradlew test"`
- 로컬 실행: Docker Desktop을 켜고 `./gradlew bootRun`
- 실행 전 환경 변수: `SIGNUP_BLOCK_SECRET`(필수), `ADMIN_PASSWORD`(첫 관리자 생성용). 테스트는 `build.gradle.kts`에서 테스트 값을 준다.

## 마이그레이션 규칙

- Flyway 마이그레이션(`src/main/resources/db/migration/`)은 **이미 있는 파일을 수정하지 않는다.** 새 변경은 다음 번호로 추가한다(현재 마지막: V10).
- JPA는 `ddl-auto: validate`다. 테이블은 Flyway만 만든다.

## Git 규칙

- 개발 브랜치: `claude/festive-einstein-k9vo8m`. 다른 브랜치에 push하지 않는다.
- `main`에 직접 push하지 않는다. PR은 사용자가 요청할 때만 만든다.
- 커밋 메시지는 `type(scope): 한국어 설명` 형식을 쓴다. 예: `feat(review): ...`, `test(report): ...`, `docs: ...`.
- 커밋 끝에 아래 두 줄을 붙인다.
  ```
  Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_014QhCZ6Z1i5oQ95LXSwWcKs
  ```
- 체크아웃, stash, reset처럼 로컬 변경을 건드리는 명령은 사용자 확인 없이 실행하지 않는다.

## 문서 위치

- 진행 상황과 다음 작업: `docs/HANDOFF.md` (기준 문서)
- 단계별 설계: `docs/work/<주제>-design.md`
- 화면 계약: `docs/work/<주제>-screens.md`
- 리뷰 기록: `docs/review.md`, `docs/work/*-review.md`
- 개발 기록: `docs/work/*-dev-report.md`, `docs/work/*-progress.md`

## 역할과 작업 흐름

역할 정의는 `.claude/agents/`에 있다. 흐름은 다음과 같다.

1. **팀장(lead)**: 작업을 나누고 사용자 결정이 필요한 것을 모아 묻는다.
2. **아키텍트(architect)**: 설계 문서와 화면 계약을 쓴다. 설계는 사용자 확정 후에만 구현한다.
3. **개발자(developer)**: 설계대로 구현하고 테스트를 쓴다. 테스트를 통과시킨 뒤 커밋한다.
4. **디자이너(designer)**: 템플릿과 스타일을 맡는다. 화면 계약의 이름을 바꾸지 않는다.
5. **리뷰어(reviewer)**: 보안과 설계 준수를 검토한다. 코드를 직접 고치지 않고 판정과 수정 요청을 쓴다.

흐름: 설계 → 사용자 확정 → 구현과 테스트 → 리뷰 → 통과 후 HANDOFF 갱신.

## 테스트 통과 기준

- 전체 테스트가 통과해야 커밋한다. 실패가 있으면 원인을 적고, 테스트를 지우거나 건너뛰지 않는다.
- 테스트 결과를 확인하지 못했으면 HANDOFF와 문서에 "미확인"이라고 적는다.
