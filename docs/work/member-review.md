# member 패키지 사전 리뷰 (보안 중심)

작성: 리뷰어 / 2026-11-08 / 기준: `docs/work/member-design.md` §5, V4 마이그레이션, SecurityConfig, 토큰 처리, 로그 노출
범위 제외: 테스트 코드, 화면 문구(개발자 작업 중). 코드는 수정하지 않았고, 테스트는 실행하지 않았다(Docker 보류).
읽은 파일: member/ 전체 Java, V4, SecurityConfig, application.yaml diff, GlobalExceptionHandler, templates/member·layout(grep).

## 판정(사전): 반려 0건 수준의 필수 수정 없음 / 권고 있음
설계서 §5의 핵심 규칙은 모두 설계대로 구현됐다. 아래는 확정 버그라기보다 개선 권고다. 필수(M) 2건은 정식 리뷰 때 반려 사유로 볼 수 있어 먼저 알린다.

## 설계 준수 확인 (OK)
- 미인증 차단: `isEnabled()=true`, `additionalAuthenticationChecks`에서 `super` 호출(비밀번호 검증) 뒤에 `DisabledException`. 비밀번호가 틀리면 BadCredentials가 먼저 나가므로 `?unverified`는 비밀번호가 맞을 때만 노출된다. `DisabledException`은 `AccountStatusException`이라 ProviderManager가 부모 매니저로 넘기지 않고 바로 던진다 → 우회 경로 없음.
- 존재하지 않는 이메일: `hideUserNotFoundExceptions` 기본값으로 BadCredentials 변환 + 타이밍 완화 동작.
- 토큰: SecureRandom 32바이트, URL-safe Base64, DB에는 SHA-256 hex만, 24시간, `used_at` 1회용. `findByTokenHash`는 유니크 인덱스를 타고 해시 비교라 타이밍 공격 여지 없음.
- CSRF 기본 활성, 로그인·로그아웃·재발송 폼 모두 `th:action` 사용(토큰 자동 삽입). `th:utext` 없음 → XSS 위험 없음. 비밀번호 필드는 `th:field`를 쓰지 않아 재렌더링 때 값이 채워지지 않음.
- 비밀번호 로그 출력 없음. 관리자 기본 비밀번호 제거, 비어 있으면 생성 건너뜀, 기존 관리자는 덮어쓰지 않음.
- V4는 설계서 §4와 동일. 엔티티 매핑(BIGSERIAL↔IDENTITY, TIMESTAMPTZ↔Instant, VARCHAR↔EnumType.STRING)은 `ddl-auto: validate`와 맞는 것으로 읽힘(실행 검증은 Docker 후).

## 필수 수정 권고 (M)

### M1. 동시 가입 시 500 오류 — `MemberService.signup`
`existsByEmail`/`existsByNickname` 확인 후 `save` 사이에 같은 값이 들어오면 `uk_member_email`/`uk_member_nickname` 위반으로 `DataIntegrityViolationException`이 발생한다. `GlobalExceptionHandler`에 처리기가 없어 500 화면이 나간다.
제안: `save`를 `saveAndFlush`로 하고 `DataIntegrityViolationException`을 `DuplicateMemberException`으로 변환(제약 이름 `uk_member_email`/`uk_member_nickname`으로 필드 판별). 설계서 §10-3의 "분기를 한 곳에 모은다"와도 맞는다.

### M2. 운영에서 인증 링크가 로그로 나감 — `LoggingVerificationNotifier`
`@Component`로 항상 활성화돼 있어 SMTP 구현체가 생기기 전까지 운영 로그에 "이메일 + 인증 토큰 원문 링크"가 INFO로 남는다. 로그를 읽을 수 있는 사람은 임의 가입자의 인증을 대신 완료할 수 있다. 설계상 감수한 사항이지만 실수로 운영에 나갈 위험이 있다.
제안: `@Profile("!prod")` 또는 `@ConditionalOnProperty(switchbase.notifier=logging, matchIfMissing=true)` 등으로 운영에서 꺼지게 하거나, 최소한 README에 "운영 전 SMTP 구현체 교체 필수" 명시. (어느 쪽을 택할지는 아키텍트/사용자 판단)

## 권고 (R, 낮음~중간)

- R1. `/signup/resend` 속도 제한 없음 + 호출마다 토큰 행이 늘고 로그 한 줄 출력. 반복 호출로 DB·로그 증가 가능. 설계서가 로그인 시도 제한만 제외했고 재발송은 언급이 없다. 간단 완화: 회원당 미사용 토큰이 N개 이상이면 새로 만들지 않기, 또는 이전 미사용 토큰 만료 처리(설계서 §5는 "만료 처리하지 않음"으로 단순화했으므로 아키텍트 결정 필요).
- R2. `/verify`가 GET으로 상태를 바꾼다. 메일 보안 스캐너·링크 미리보기가 링크를 먼저 열면 토큰이 소진돼 사용자는 "이미 사용된 링크"를 본다. SMTP 도입 시점에 "GET은 확인 화면, POST로 소비" 방식 검토.
- R3. 레이아웃이 `cdn.jsdelivr.net`의 htmx를 SRI(`integrity`) 없이 로드하고, `/verify?token=...` 페이지도 같은 레이아웃을 쓴다. 현대 브라우저 기본 Referrer-Policy는 origin만 보내 토큰 유출 가능성은 낮지만, CDN 변조 시 모든 페이지(로그인 포함)에서 스크립트 실행이 가능하다. `integrity`+`crossorigin` 추가, 응답에 `Referrer-Policy: no-referrer` 헤더(Security `headers` 설정) 권장. (layout.html은 디자이너 담당 → 아키텍트 경유)
- R4. 설계서 §7은 "`POST /signup/resend`는 `email` 모델 속성을 넣지 않는다"인데 `MemberController.resend`는 정규화한 입력 이메일을 모델에 넣는다. 존재 여부와 무관하게 입력값을 그대로 돌려주므로 정보 노출은 없다. 화면 문서(member-screens.md)와 템플릿이 어느 쪽을 기준으로 하는지 맞춰야 한다(설계서 위반 여부만 확인).
- R5. 닉네임: `@Size`는 trim 전 값에 적용되고 서비스는 `trim()`해서 저장한다. `" a"`는 검증을 통과해 1글자 닉네임으로 저장된다. 컨트롤러/서비스에서 trim 후 재검증하거나 `@Pattern`으로 앞뒤 공백 금지. 이메일도 `@Email` 검증은 trim 전 값 기준(공백 포함 입력은 거부되므로 안전한 쪽).
- R6. `AdminAccountInitializer`: `ADMIN_NICKNAME`이 20자 초과/1자이거나 `ADMIN_EMAIL` 형식이 잘못돼도 검증 없이 저장 시도 → 20자 초과는 DB 오류로 기동 실패. 비밀번호와 같이 WARN 후 건너뛰도록 길이 검증 추가 권장(설계서 §6 "기동 실패가 아니라 WARN" 취지). 다중 인스턴스 동시 기동 시 유니크 위반 가능성은 단일 서버 전제라 무시.
- R7. 토큰 소비 경합: 같은 토큰을 동시에 두 번 열면 둘 다 `usedAt == null`을 읽고 성공할 수 있다(결과는 둘 다 인증 완료라 피해 없음). 엄밀히 하려면 `UPDATE ... SET used_at=? WHERE token_hash=? AND used_at IS NULL AND expires_at>?`의 영향 행 수로 판단.
- R8. 만료·사용된 토큰 정리 작업이 없다(행이 계속 쌓임). 2단계 후반에 정리 스케줄 추가 고려. 현재 규모에서는 문제 없음.

## V4 마이그레이션 메모 (참고)
- `password_hash VARCHAR(100)`: 현재 `{bcrypt}`+60자 = 68자라 충분. 나중에 인코더를 argon2 등으로 바꾸면 접두사까지 100자를 넘을 수 있다. 지금 바꾸려면 V4는 수정 금지이므로 필요해지면 V5로 확장.
- 이메일 유니크는 대소문자 구분이다. 앱이 항상 소문자로 정규화해서 현재는 안전하지만, 다른 경로(수동 INSERT, 향후 코드)로 `A@x.com`/`a@x.com`이 공존할 수 있다. 방어하려면 `CHECK (email = lower(email))`를 V5로 추가 가능(선택).
- 닉네임은 대소문자 구분 유니크(설계서 결정). `Admin`/`admin` 사칭 가능성은 설계서에서 감수한 항목.
- `email_verification_token`에 `expires_at` 인덱스 없음 — 정리 작업을 만들 때 함께 검토.

## 확인하지 못한 것
- `./gradlew test` 미실행(Docker 보류). 컴파일·기동·`validate` 통과 여부는 정식 리뷰에서 확인.
- Security 7의 `DaoAuthenticationProvider(PasswordEncoder)` 생성자와 `additionalAuthenticationChecks` 시그니처는 소스를 읽어 확인했을 뿐 컴파일 결과는 보지 못했다.
- 템플릿은 보안 관련 패턴(utext, csrf, 비밀번호 재채움)만 grep으로 확인했고 화면 내용은 검토하지 않았다.

---

# 정식 리뷰 (2026-11-08, 리뷰어)

기준: 개발자 보고서 `docs/work/member-dev-report.md` §7·§8, 실제 코드. 상세 1차 결과는 `docs/review.md`.

## 판정: 통과 (반려 0건)

## 테스트 근거
- 1차 정식 리뷰(12:05 실행): 42개 중 4개 실패 → 반려 4건(layout nickname 1, `redirectedUrlPattern` 3).
- 재검토: `build/test-results/test/*.xml` 9개 스위트 합계 **42개, 실패 0, 오류 0, 건너뜀 0**.
  AdminAccessTest 3, SwitchServiceTest 7, SchemaMigrationTest 2, SwitchbaseApplicationTests 1, MemberLoginFlowTest 18, MemberServiceUnitTest 5, MemberServiceTest 1, AdminAccountInitializerValidationTest 3, LoggingVerificationNotifierProfileTest 2.
- 리포트(12:08) 이후 `src/` 아래 수정된 파일 없음 → 통과한 코드와 현재 코드가 같다.
- 리뷰어가 직접 `sg docker -c "./gradlew test"`를 재실행했으나 25분 뒤 Gradle이 `in-progress-results-generic.bin NoSuchFileException`으로 종료되어 이번 실행 자체의 결과는 얻지 못했다(1차 때와 같은 현상, 같은 시간대 개발자 실행과 `build/` 폴더를 공유해 충돌한 것으로 추정, 코드 문제 아님). 통과 판정은 위 XML과 소스 불변 확인에 근거한다.

## 반려 4건 해소 확인
| 항목 | 확인 |
|---|---|
| layout `principal.nickname` → AdminAccessTest 실패 | layout이 `MemberUserDetails`일 때만 닉네임, 아니면 `#authentication.name`. AdminAccessTest 3개 통과 |
| `redirectedUrlPattern` 3곳 | `redirectedUrl("/login")`, `("/switches")`, `("/login?logout")`로 교체됨(MemberLoginFlowTest 94·223·261행 등). 통과 |

## 사전 리뷰 항목 최종 상태
M1·M2·R1·R3·R4·R5·R6·로그아웃 위치 모두 반영 완료. M2(prod 기동 실패)는 사용자가 의도로 확인함. 남은 권고(R2 GET /verify, R7·R8, V4 참고 사항)는 반려 사유가 아니며 SMTP 도입·2단계 후반에 재검토.

## 개발자 전달 사항
없음(수정 요청 없음). 통과이므로 개발자가 커밋 진행 가능: `feat(member): ...`, `test(member): ...`, `docs: ...`를 유형별로 나눠서, push 금지.
