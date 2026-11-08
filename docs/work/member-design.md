# 2단계 회원가입·로그인 설계 (member)

작성: 아키텍트 / 2026-11-08 / 기준: docs/HANDOFF.md §4 2단계 첫 항목

## 1. 목표와 범위

포함: `/login`, `/signup`, `/verify`, member 엔티티·V4 마이그레이션, 역할 ADMIN/USER, 회원 기반 UserDetailsService,
`switchbase.admin.*`로 첫 관리자 초기화, 이메일 인증(링크를 콘솔 로그에만 출력), 실제 로그인 흐름 통합 테스트.
제외: SMTP 발송, SNS 로그인, 비밀번호 재설정, 로그인 시도 제한, 회원 탈퇴(2단계 후반), 프로필 수정.

## 2. 현재 문제 (수정 대상)

- `application.yaml`의 `spring.switchbase.admin.*`는 접두사가 `spring.`이라 잘못된 위치. 최상위 `switchbase.admin.*`로 옮긴다.
- `SecurityConfig`가 `/login`을 지정했으나 화면·UserDetailsService가 없어 404 + 임시 비밀번호 생성.
- `AdminAccessTest`는 `@WithMockUser`라 실제 로그인을 검증하지 못함 → 통합 테스트 추가(기존 테스트는 유지).

## 3. 패키지와 파일 (`com.switchbase.member`)

| 파일 | 내용 |
|---|---|
| `Role` | enum `USER`, `ADMIN` |
| `Member` | JPA 엔티티 (아래 §4) |
| `MemberRepository` | `findByEmail`, `existsByEmail`, `existsByNickname` |
| `EmailVerificationToken` / `...Repository` | 인증 토큰 엔티티 |
| `MemberUserDetails` | `UserDetails` 구현. email, passwordHash, nickname, role, emailVerified. `isEnabled()`는 항상 true(§5 정정). 권한은 `ROLE_` + role |
| `MemberUserDetailsService` | email(소문자 정규화)로 조회. 없으면 `UsernameNotFoundException` |
| `SignupForm` | record: email, nickname, password, passwordConfirm + Bean Validation |
| `MemberService` | `signup(form)`, `verify(rawToken)`, `resendVerification(email)` |
| `VerificationNotifier` | 인터페이스 `send(Member, String verifyUrl)`. 구현 `LoggingVerificationNotifier`(로그 출력). SMTP는 나중에 구현체만 교체 |
| `MemberController` | `GET/POST /signup`, `GET /verify`, `POST /signup/resend`, `GET /login` |
| `AdminAccountInitializer` | `ApplicationRunner`. §6 |
| `AdminProperties` | `@ConfigurationProperties("switchbase.admin")` record: email, password, nickname |

`common/SecurityConfig` 수정(개발자): 아래 §5. 기존 `keyswitch` 패키지는 건드리지 않는다.

## 4. 데이터: `V4__create_member.sql` (기존 V1~V3 수정 금지)

```sql
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
```

엔티티 매핑은 `ddl-auto: validate`를 통과해야 한다(컬럼 타입 일치 확인). 기존 엔티티 스타일(keyswitch)을 따른다.

## 5. 인증·보안 규칙

- 비밀번호: 기존 `PasswordEncoder`(delegating, bcrypt) 사용. 글자 수 8자 이상, 그리고 **UTF-8 바이트 수 72 이하**
  (bcrypt 한계는 글자가 아니라 바이트라 한글은 24자 정도에서 초과). 바이트 검사는 커스텀 검증 어노테이션
  (예: `@MaxUtf8Bytes(72)`) 또는 `SignupForm`의 `@AssertTrue`로 하고, 초과 시 폼 오류로 안내한다
  ("비밀번호가 너무 깁니다. 한글은 약 24자까지 가능합니다" 류 문구는 디자이너가 정함). 그 외 조합 규칙 없음.
- 이메일: 형식 검증, 최대 255자, 저장 전 `trim().toLowerCase()`. 닉네임: 2~20자, 중복 불가(대소문자 구분 없이 비교는 하지 않음).
- 가입 직후 `USER`, `email_verified=false`. **미인증 회원은 로그인 불가**(`enabled=false` → `DisabledException`).
- 로그인 실패는 이유를 구분해 `/login?error`(아이디·비밀번호 불일치), `/login?unverified`(미인증)로 보낸다.
  `SimpleUrlAuthenticationFailureHandler`를 쓰거나 `AuthenticationFailureHandler`로 `DisabledException`만 분기.
  **정정(팀장 지적, 2026-11-08)**: 처음 문서의 "미인증 여부는 비밀번호가 맞을 때만 노출"은 틀렸다.
  `AbstractUserDetailsAuthenticationProvider`는 `preAuthenticationChecks`(`isEnabled` 등)를 비밀번호 확인보다 먼저 실행한다.
  그래서 `UserDetails.isEnabled()=false`로 막으면, 존재하는 이메일은 비밀번호를 몰라도 `DisabledException`이 나와
  "가입돼 있고 미인증"임이 누구에게나 드러난다.
  **채택 방식**: `MemberUserDetails.isEnabled()`는 항상 `true`로 두고, 미인증 차단은 **비밀번호 확인 뒤** 단계에서 한다.
  `DaoAuthenticationProvider` 빈을 직접 만들어 `setPostAuthenticationChecks(user -> 미인증이면 DisabledException)`을 건다
  (Security 7에서 이 API 위치·시그니처는 개발자가 확인. 막히면 리서처 호출). 이러면 `?unverified`는 비밀번호가 맞을 때만 보인다.
  남는 노출: 비밀번호를 아는 본인에게만 보이므로 문제 없음. 단 가입 폼의 중복 이메일 안내(§10-3)는 별개로 계정 존재를 알려주므로,
  §10-3을 "알려줌"으로 확정해도 이 post-check 방식은 유지한다(비용이 거의 없고 로그인 화면에서는 노출을 막음).
- 접근 규칙: `/admin/**` ADMIN, `/login` `/signup` `/signup/resend` `/verify` `/css/**` 및 나머지는 permitAll(현 정책 유지).
  이미 로그인한 사용자가 `/login`·`/signup`에 오면 `/switches`로 리다이렉트(컨트롤러에서 처리).
- **응답 헤더(2026-11-08, 사용자 결정, 사전 리뷰 R3)**: `Referrer-Policy: no-referrer`를 Security 헤더 설정으로 모든 응답에 추가한다(`/verify?token=` URL이 외부로 새지 않게).
  `layout.html`의 외부 스크립트(htmx CDN)에는 `integrity`(SRI)와 `crossorigin="anonymous"`를 붙인다. 이유: CDN 변조 시 로그인 화면까지 스크립트가 실행될 수 있다.
- CSRF는 켠 채 유지(Thymeleaf `th:action`이 토큰을 자동 삽입).
- 인증 토큰: `SecureRandom` 32바이트 → URL-safe Base64(패딩 없음). DB에는 SHA-256 hex만. 유효 24시간, 1회용(`used_at`).
  재발송 시 해당 회원의 미사용 토큰은 만료 처리하지 않고 새 토큰만 추가(단순화).
  **변경(2026-11-08, 사용자 결정, 사전 리뷰 R1)**: 해당 회원의 미사용·미만료 토큰이 5개 이상이면 새 토큰을 발급하지 않고 로그도 남기지 않는다(응답은 동일 안내).
  이유: `/signup/resend`가 무제한이라 반복 호출로 토큰 행과 로그가 무한히 늘 수 있다. 요청 속도 제한은 도입하지 않고 개수 상한으로만 막는다(단순화 유지). 인증 성공 시 `email_verified=true`.
- 인증 링크: `${switchbase.base-url}/verify?token=...`. `switchbase.base-url` 기본 `http://localhost:8090`.
  **변경(2026-11-08, 사용자 결정, 사전 리뷰 M2)**: `LoggingVerificationNotifier`는 `@Profile("!prod")`로 운영에서 꺼진다.
  이유: 운영 로그에 토큰 원문 링크가 남으면 로그를 읽을 수 있는 사람이 임의 가입자의 인증을 대신 완료할 수 있다.
  결과: `prod` 프로필에서는 `VerificationNotifier` 빈이 없어 기동이 실패한다(SMTP 구현체 없이 운영에 나가는 것을 막는 의도). SMTP 구현체는 `@Profile("prod")` 등으로 추가.
  `LoggingVerificationNotifier`는 `INFO`로 `[DEV] 이메일 인증 링크 (email=..): url` 한 줄만 출력. 비밀번호는 로그에 절대 쓰지 않는다.
- 가입 시 이미 있는 이메일이면 "이미 사용 중인 이메일입니다"를 폼 오류로 표시(계정 존재 노출은 소규모 취미 서비스라 감수 — 사람이 판단할 항목, §9 참고).
- `/signup/resend`는 이메일 존재 여부와 상관없이 같은 안내 문구로 응답하고, 미인증 회원일 때만 새 토큰 발급·로그 출력.
- 레이아웃은 로그인 후 `sec:authentication="name"`(이메일) 대신 닉네임을 쓰도록 `principal.nickname`로 바꾼다(디자이너).

## 6. 첫 관리자 초기화

설정(`application.yaml`, 최상위):
```yaml
switchbase:
  base-url: ${BASE_URL:http://localhost:8090}
  admin:
    email: ${ADMIN_EMAIL:admin@switchbase.local}
    nickname: ${ADMIN_NICKNAME:admin}
    password: ${ADMIN_PASSWORD:}      # 기본값 없음. 비어 있으면 관리자 생성을 건너뛰고 WARN 로그
```
- `AdminAccountInitializer`: 기동 시 `password`가 비어 있지 않고 해당 email의 회원이 없으면 `ADMIN`, `email_verified=true`로 생성.
  이미 있으면 **아무것도 바꾸지 않는다**(재기동 때 비밀번호·역할을 덮어쓰지 않음).
- 기존 `ADMIN_USERNAME`/`admin1234` 기본값은 제거(저장소에 약한 기본 비밀번호를 두지 않음). 로컬 개발은 환경 변수 `ADMIN_PASSWORD`로 지정.
  README에 한 줄 안내 추가(개발자, docs 커밋).
- 값 검증: 비밀번호 8자 미만이면 기동 실패가 아니라 WARN 후 건너뜀.

## 7. 화면 (디자이너 담당: `docs/work/member-screens.md` + `templates/member/*.html` + `main.css` 추가분)

| URL | 템플릿 | 모델 속성 |
|---|---|---|
| `GET /login` | `member/login.html` | 쿼리 `error`, `unverified`, `logout` 존재 여부로 메시지 분기. 이메일 입력 name=`email`, 비밀번호 name=`password` |
| `GET/POST /signup` | `member/signup.html` | `signupForm`(email, nickname, password, passwordConfirm), `BindingResult`. 오류는 필드별 `th:errors` |
| `POST /signup` 성공 | `member/signup-done.html` | `email`. "인증 링크를 보냈다"는 안내(개발 중에는 서버 로그에 출력됨을 한 줄 표시하지 않음 — 운영 문구로 작성) |
| `GET /verify` | `member/verify-result.html` | `success`(boolean), 실패 시 재발송 폼(email 입력, `POST /signup/resend`) |
| `POST /signup/resend` | `member/signup-done.html` 재사용 | 동일 안내 문구. `email` 속성은 넣지 않는다 |

추가 승인(2026-11-08): `login.html`의 `?unverified` 안내 아래에 인증 메일 재발송 폼(`POST /signup/resend`, 필드 `email`)을 둔다.
화면 문구·계약의 상세는 `docs/work/member-screens.md`가 기준.

문구는 한국어, 스위치 이름 등은 영문 그대로. 기존 `fragments/layout.html`의 head/header/footer 프래그먼트와 `main.css` 스타일을 재사용한다.
검증 메시지 문구(예: "비밀번호는 8자 이상 72자 이하여야 합니다")는 디자이너가 정하고 개발자가 `messages` 또는 어노테이션 message에 반영한다.

## 8. 테스트 (개발자: `src/test/java/com/switchbase/member/`)

모두 `@SpringBootTest` + `@AutoConfigureMockMvc` + `TestcontainersConfiguration`, 실제 필터체인 사용(`@WithMockUser` 금지).
테스트용 `VerificationNotifier` 캡처 빈(`@TestConfiguration`, `@Primary`)으로 발송된 링크를 읽는다.
관리자 시드는 `@TestPropertySource(properties = {"switchbase.admin.password=..."})`.

`MemberLoginFlowTest`:
1. `GET /login` → 200, 로그인 폼 존재 (404 회귀 방지)
2. 가입 → 인증 전 로그인 실패 (`/login?unverified`) → 캡처한 링크로 `/verify` 성공 → `formLogin("/login").user("email", ...)` 성공, 인증됨
3. 잘못된 비밀번호 → `/login?error`. **미인증 회원 + 틀린 비밀번호 → `/login?error`(`unverified` 아님)**, 미인증 + 맞는 비밀번호 → `/login?unverified`
   (미인증 여부 노출 방지 회귀 테스트). 한글 25자 비밀번호(75바이트) 가입 → 폼 오류, 24자(72바이트) → 성공
4. 같은 토큰 재사용·만료 토큰·알 수 없는 토큰 → 실패 화면
5. 중복 이메일·중복 닉네임·짧은 비밀번호·비밀번호 확인 불일치 → 200 + 오류
6. 시드된 관리자로 로그인 → `/admin/switches/new` 200; USER로 로그인 → `/admin/**` 403; 비로그인 → `/login`으로 리다이렉트
7. 관리자 초기화: 재실행해도 기존 관리자 비밀번호·역할 불변(단위/슬라이스 테스트 가능)
8. 로그아웃(POST /logout, CSRF) 후 `/admin/**` 접근 불가

`MemberServiceTest`(선택): 토큰 해시 저장·만료 시각·1회용 확인. 기존 `AdminAccessTest`의 `redirectedUrl("/login")`이 실제 값(`http://localhost/login`)과 맞는지는 개발자가 실행해서 확인하고 필요하면 고친다.

## 9. 작업 분담과 순서

1. 디자이너: §7 화면 문구·템플릿·CSS. 입력: 이 문서. 산출: `docs/work/member-screens.md`, `templates/member/`, `layout.html`의 닉네임 표기, `main.css`.
2. 개발자: §3~6, §8. 템플릿은 디자이너 산출물을 사용(없으면 모델 속성 계약대로 빈 껍데기 말고 디자이너 완료를 기다림). 디자이너와 병행하되 Java/SQL/테스트부터 시작.
3. 리뷰어: 개발자 완료 후 보안(토큰, 인증 분기, CSRF, 로그 노출), 마이그레이션, 테스트 품질 검토.
4. 리서처: 이번 범위에서는 호출하지 않음(사용 라이브러리 모두 기존 의존성: Security 7 formLogin, Thymeleaf-extras). 개발자가 Security 7 API 차이로 막히면 호출.
5. 리뷰 통과 후 개발자가 커밋(한 줄, 유형 하나씩: `feat(member): ...`, `test(member): ...`, `docs: ...`). push 금지.

## 10. 결정 현황 (팀장 회신 2026-11-08)

1. 미인증 회원 로그인 차단 — **확정**. 차단은 비밀번호 확인 뒤(post-check)에 한다(§5 정정).
2. 약한 기본 관리자 비밀번호 제거, `ADMIN_PASSWORD` 필수 — **확정**.
3. 가입 시 중복 이메일 안내(계정 존재 노출) — **A(알려줌)로 사용자 확정(2026-11-08)**. 선택지 기록:
   - A. 알려줌(현재 구현): 사용성 좋음, 이메일이 가입돼 있는지 누구나 알 수 있음.
   - B. 알리지 않음: 항상 "인증 메일을 보냈다" 응답. 이미 있는 이메일이면 기존 회원에게 안내 메일(개발 중엔 로그)만 보냄.
     노출은 막지만 가입 실패 사유를 사용자가 모르고, 구현이 늘며(SMTP 전에는 로그로만 확인 가능), 닉네임 중복은 어차피 알려야 해서 완전한 은닉은 아님.
   - 권장: A(소규모 커뮤니티, 닉네임도 공개 정보). 개발자는 A로 구현하되 `MemberService.signup`의 분기를 한 곳에 모아 B로 바꾸기 쉽게 둔다.
4. 비밀번호 규칙 — **확정**: 8자 이상 + UTF-8 72바이트 이하(§5).
5. 인증 링크 유효 24시간 — **확정**.

### 보류 중인 환경 이슈
Docker 그룹이 현재 세션에 반영되지 않아 Testcontainers 실행 불가(사용자가 새 세션에서 처리 예정). 그때까지 개발자는 Java/SQL/테스트 작성만 하고 `./gradlew test`는 보류한다.
