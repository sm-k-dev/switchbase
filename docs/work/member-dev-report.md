# member 개발 보고 (개발자 → 아키텍트)

작성: 개발자 / 2026-11-08 / 기준: member-progress.md §2, member-design.md §8

## 1. 실행 결과

- `./gradlew compileTestJava`: **성공** (main + test 컴파일 통과)
- `./gradlew test`: **미실행**. Docker 권한 문제로 지시대로 생략. 테스트 통과 여부는 아직 확인되지 않음.

## 2. §2 수정 (member-progress.md)

1. `MemberController.resend`: 모델에 `email`을 넣지 않도록 한 줄 제거 (member-screens §3).
2. 검증 문구를 member-screens §4.4 문구로 통일:
   - `SignupForm`: 이메일(빈 값·형식·255자), 닉네임(빈 값·2~20자), 비밀번호(빈 값·8자 미만·72바이트 초과), 비밀번호 확인(빈 값)
   - `MemberController`: 비밀번호 불일치 → "비밀번호가 서로 다릅니다."
   - `MemberService`의 중복 안내("이미 사용 중인 이메일입니다" / "이미 사용 중인 닉네임입니다")는 이미 일치하여 변경 없음.

## 3. 추가 수정 (컴파일 오류, 설계 변경 없음)

- `common/SecurityConfig.java`: Security 7.1.1 API에 맞춤.
  - `new DaoAuthenticationProvider(passwordEncoder)` → `new DaoAuthenticationProvider(userDetailsService)`
  - `provider.setUserDetailsService(...)` 호출 제거, `provider.setPasswordEncoder(passwordEncoder)` 추가
  - 미인증 차단을 비밀번호 확인 뒤(`additionalAuthenticationChecks`)에 두는 방식은 그대로.

## 4. 테스트 작성 (§8)

| 파일 | 내용 |
|---|---|
| `test/.../member/CapturingNotifierConfig.java` | `@Primary` 캡처 Notifier. 발송된 인증 링크를 메모리에 보관, `lastToken(email)`로 토큰 원문 조회 |
| `test/.../member/MemberLoginFlowTest.java` | §8 항목 1~8 (17개 테스트) |
| `test/.../member/MemberServiceTest.java` | 토큰은 SHA-256 해시로만 저장, 만료 24시간 (1개) |

`MemberLoginFlowTest` 항목별 대응:
1. `/login` 200, 뷰 `member/login`
2. 가입 → `/login?unverified` → `/verify` 성공 → 로그인 성공(`authenticated`)
3. 미인증+틀린 비밀번호 → `/login?error` / 미인증+맞는 비밀번호 → `/login?unverified` / 없는 이메일 → `/login?error` / 틀린 비밀번호 → `/login?error`. 한글 25자(75B) 폼 오류, 24자(72B) 성공
4. 토큰 재사용, 만료(`expires_at`을 JDBC로 과거로 갱신), 알 수 없는/없는 토큰 → `success=false`
5. 중복 이메일·닉네임, 7자 비밀번호, 비밀번호 확인 불일치 → 필드 오류
6. 시드 관리자 → `/admin/switches/new` 200 / USER → 403 / 비로그인 → `/login` 리다이렉트
7. 관리자 초기화 재실행 시 해시·역할·닉네임 불변 (`AdminAccountInitializer`를 다른 값으로 직접 실행)
8. POST `/logout` 후 `/admin/**` 접근 불가

테스트는 `@WithMockUser`를 쓰지 않고 실제 `/login` POST(`post("/login").param("email"...)` + `csrf()`)로 로그인한다.
설계서의 `formLogin("/login").user(...)` 대신 일반 `post`를 쓴 이유: 파라미터 이름을 명확히 두기 위해서이며 동작은 같다.

## 5. 확인이 필요한 점 (실행 전이라 미검증)

- `AdminAccessTest.anonymousIsSentToLogin`의 `redirectedUrl("/login")`: 실제 값이 `http://localhost/login`이면 실패한다. 이번 작업에서는 고치지 않았다. 테스트 실행 후 확인 필요.
- 로그아웃 후 이동 위치: `SecurityConfig`는 `logoutSuccessUrl("/")`인데, member-screens §2는 `/login?logout`(문구 "로그아웃되었습니다.")을 정했다. 아키텍트 결정 필요. 지금은 수정하지 않았다.
- `MemberLoginFlowTest`의 `redirectedUrlPattern("**/login")` 등 일부 매처는 Spring Security 7 / MockMvc 동작 기준으로 썼으며, 실행해 봐야 맞는지 안다.

## 6. 커밋

하지 않음 (지시대로). 변경 파일:
- 수정: `common/SecurityConfig.java`, `member/MemberController.java`, `member/SignupForm.java`
- 신규: `test/.../member/CapturingNotifierConfig.java`, `MemberLoginFlowTest.java`, `MemberServiceTest.java`, `docs/work/member-dev-report.md`

## 7. 2차 반영 (사전 리뷰 M1·M2·R1·R5·R6, Referrer-Policy, 로그아웃 위치)

| 항목 | 반영 내용 | 테스트 |
|---|---|---|
| 로그아웃 위치 | `SecurityConfig`: `logoutSuccessUrl("/login?logout")` (screens §2) | `MemberLoginFlowTest.logoutEndsAdminAccess` |
| M1 동시 가입 | `MemberService.signup`: `saveAndFlush` + `DataIntegrityViolationException`을 제약 이름(`uk_member_email`/`uk_member_nickname`)으로 `DuplicateMemberException` 변환. 그 밖의 위반은 그대로 던짐 | `MemberServiceUnitTest` 3개 (Mockito) |
| M2 운영 로그 | `LoggingVerificationNotifier`: `@Profile("!prod")` | `LoggingVerificationNotifierProfileTest` 2개 |
| R1 재발송 상한 | `MemberService.resendVerification`: 미사용·미만료 토큰이 5개 이상이면 발급·로그 생략. `EmailVerificationTokenRepository.countByMemberIdAndUsedAtIsNullAndExpiresAtAfter` 추가 | `MemberServiceUnitTest` 2개 |
| R5 닉네임 trim | `SignupForm.setNickname`에서 trim 후 검증 | `MemberLoginFlowTest.nicknameIsTrimmedBeforeValidation` |
| R6 관리자 설정 | `AdminAccountInitializer`: ADMIN_EMAIL 형식(정규식)·길이, ADMIN_NICKNAME 2~20자(null·공백 포함) 검사 실패 시 WARN 후 건너뜀 | `AdminAccountInitializerValidationTest` 3개 |
| Referrer-Policy | `SecurityConfig`: `headers().referrerPolicy(NO_REFERRER)` | `MemberLoginFlowTest.loginPageIsServed` (헤더 검사) |

- `compileTestJava`: **성공** (2차 변경 포함).
- `./gradlew test`: **미통과 확인 상태**. 팀장 지시로 `sg docker -c "./gradlew test"`를 실행하려 했으나, 자동 권한 분류기가 거부했다. 우회하지 않았다. 따라서 **테스트 실행 결과는 아직 없다.** 허가 후 바로 실행해야 한다.
- 커밋: 하지 않음.

### 7-1. 실행 전 확인 필요 (테스트 실행 시 깨질 수 있는 부분)
- `AdminAccessTest.anonymousIsSentToLogin`의 `redirectedUrl("/login")`.
- `MemberLoginFlowTest`의 `redirectedUrlPattern("**/login")`, `authenticated()` 매처 동작.
- `MemberServiceUnitTest`의 Mockito 스텁 (`saveAndFlush`, 카운트 쿼리 시그니처).

## 8. 테스트 실행 결과 (sg docker -c "./gradlew test")

- 결과: **BUILD SUCCESSFUL, 42개 전부 통과** (실패 0, 건너뜀 0)
- 수정: `MemberLoginFlowTest`의 `redirectedUrlPattern`을 실제 값 `redirectedUrl("/login")`·`redirectedUrl("/switches")`로 교체 (3곳).
- `AdminAccessTest.adminCanOpenForm`: 디자이너의 layout 수정 후 3개 전부 통과.
- 스위트별: SwitchbaseApplicationTests 1, SwitchServiceTest 7, SchemaMigrationTest 2, AdminAccessTest 3, MemberLoginFlowTest 18, MemberServiceUnitTest 5, MemberServiceTest 1, AdminAccountInitializerValidationTest 3, LoggingVerificationNotifierProfileTest 2.
- 커밋: 하지 않음 (리뷰 통과 후).
