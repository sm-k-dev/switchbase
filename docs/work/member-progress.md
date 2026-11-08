# 회원가입·로그인 진행 현황 (member, 2단계)

작성: 아키텍트 / 2026-11-08 / 기준: member-design.md, member-screens.md

## 1. 설계 대 코드 대조

| 항목 | 상태 |
|---|---|
| V4 마이그레이션, Role/Member/Repository, 토큰 엔티티 | 있음 (미커밋) |
| MemberUserDetails(Service), SignupForm, MaxUtf8Bytes, MemberService, Controller | 있음 |
| Notifier + Logging 구현, AdminProperties, AdminAccountInitializer | 있음 |
| SecurityConfig (post-check로 미인증 차단, 실패 분기) | 있음 (`additionalAuthenticationChecks` 방식) |
| application.yaml `switchbase.*` 이동 | 있음 |
| 템플릿 4종, layout 닉네임, CSS | 있음 |
| **테스트 (`src/test/.../member/`)** | **없음** — MemberLoginFlowTest, 캡처 Notifier, 관리자 초기화 테스트 모두 미작성 |
| README 관리자 안내(`ADMIN_PASSWORD`) | 미확인, 개발자 확인 |
| `AdminAccessTest`의 `redirectedUrl("/login")` | 실제 값 `http://localhost/login`일 수 있음 — 실행해서 확인 |

## 2. 설계·화면 계약과 어긋난 곳 (개발자 수정 요청)

1. `MemberController.resend`가 모델에 `email`을 넣는다. 화면 계약(screens §3): 재발송에는 넣지 않는다. 한 줄 제거.
2. 검증 문구가 screens §4.4와 다르다 (SignupForm, MemberController, MemberService의 중복 안내, 비밀번호 확인 불일치 "서로 다릅니다" 등). screens §4.4 문구로 통일.
3. 이메일 정규화 후 255자 검사 등은 현 상태로 충분. 추가 요청 없음.

## 3. 차단 요인

- 현재 세션 사용자가 docker 그룹에 없어 `docker ps`가 permission denied. Testcontainers 실행 불가. 우회하지 않는다. 사용자가 새 세션/그룹 반영 후 `./gradlew test` 실행 필요.
- 따라서 "테스트 통과 → 리뷰 → 커밋" 중 **테스트 작성까지만** 지금 진행 가능. 통과 확인 전 커밋하지 않는다.

팀장 확인(2026-11-08): 원인은 /etc/group에는 docker 그룹이 있으나 tmux 서버가 그룹 추가 전에 떠서 반영 안 됨. 사용자가 새 로그인 셸에서 tmux를 다시 띄워야 하며 그때까지 테스트 보류. 리뷰어의 코드 리뷰는 미리 시작(결과: `docs/work/member-review.md`). 커밋은 테스트 통과 후에만.

## 3-1. 사전 리뷰 결과 처리 (`member-review.md`)

- 개발자 수정 지시: M1(동시 가입 500), R4(= §2-1), R5(닉네임 trim), R6(관리자 설정 검증).
- 결정 필요(팀장→사용자): M2 운영에서 인증 링크 로그 출력(Profile/속성으로 끄기 vs README 경고), R1 재발송 토큰 제한(설계 §5 "만료 처리 안 함" 변경 여부), R3 layout.html htmx SRI·Referrer-Policy(디자이너 파일, 승인 시 지시).
- 보류(이번 범위 밖, SMTP 도입 시): R2 GET /verify 소비, R7 토큰 소비 경합, R8 토큰 정리.

## 3-2. 사용자 결정 반영 (2026-11-08)

M2(`@Profile("!prod")`), R1(미사용 토큰 5개 상한), R3(htmx SRI+crossorigin, `Referrer-Policy: no-referrer`) — 설계 §5에 변경 이유 기록 완료.
담당: 개발자(M2·R1·헤더+테스트), 디자이너(layout.html SRI — **완료**, htmx 2.0.4, sha384, `layout.html:8-10` 확인). 주의: `prod` 프로필은 SMTP 구현체가 생기기 전까지 기동 실패(의도). 배포 문서(README)에 프로필 사용 여부 확인 필요.

## 3-3. 개발자 1차 보고 (`member-dev-report.md`)

- §2 수정 2건 완료, 테스트 18개 작성(MemberLoginFlowTest 17, MemberServiceTest 1), `compileTestJava` 성공, `test` 미실행.
- SecurityConfig를 Security 7.1.1 API에 맞게 수정(`DaoAuthenticationProvider(userDetailsService)` + `setPasswordEncoder`). 설계 변경 아님.
- 결정(아키텍트): 로그아웃 후 이동은 screens §2 기준 `/login?logout` (login.html이 이미 `param.logout` 처리). `SecurityConfig`와 테스트를 맞춘다.
- 남음: M1·R5·R6·M2·R1·Referrer-Policy 반영과 테스트 → 그 뒤 리뷰어 정식 리뷰.
- 실행 후 확인: `AdminAccessTest`의 `redirectedUrl("/login")`, 일부 `redirectedUrlPattern` 매처.

## 3-4. 사용자 답변 (2026-11-08)

배포 전이며 나중에 EC2에서 `SPRING_PROFILES_ACTIVE=prod`로 실행 예정. prod에서 SMTP 구현이 없으면 기동 실패하는 현 설계가 맞음(이메일 없이 배포되는 것을 막음). SMTP 서비스 선택은 배포 전에 따로 결정, 지금은 로그 출력으로 진행. `HANDOFF.md` §5에 항목 추가함. 테스트는 `sg docker -c "./gradlew test"`로 실행 예정(팀장 전달).

## 3-5. 첫 테스트 실행 (아키텍트, `sg docker -c "./gradlew test"` 정상 동작)

42개 중 4개 실패, 원인 2가지:
1. 테스트 매처: `redirectedUrlPattern("**/login")`, `("**/switches")`는 실제 리다이렉트 값(`/login`, `/switches`)과 불일치 → `redirectedUrl(...)`로 교체 (MemberLoginFlowTest 3개: unverifiedMemberIsBlocked…, anonymousIsSentToLogin…, logoutEndsAdminAccess). 개발자.
2. layout.html `principal.nickname`: `@WithMockUser`(principal=String)인 기존 `AdminAccessTest.adminCanOpenForm`에서 NotReadablePropertyException. 실제 회원 로그인에는 문제 없으나 다른 principal(향후 OAuth2 포함)에 취약 → MemberUserDetails면 닉네임, 아니면 `#authentication.name`으로 방어. 디자이너.
참고: 설계 §8 "기존 테스트 유지"가 이 회귀를 잡아줌. `AdminAccessTest`의 `redirectedUrl("/login")`은 통과(실제 값 `/login`), 별도 수정 불필요.

## 3-6. 정식 리뷰 결과 (`docs/review.md`)

판정: 반려 4건(§3-5와 동일한 원인 — layout 방어 1건은 디자이너, 매처 3건은 개발자). 운영 코드 보안 결함 없음. 사전 리뷰 반영(M1·M2·R1·R3·R4·R5·R6, 로그아웃 위치) 확인됨. M2의 prod 기동 실패는 사용자 확인한 의도대로(§3-4). 레이아웃은 (a)안(instanceof 방어)으로 결정. 재리뷰 조건: 위 수정 후 `sg docker -c "./gradlew test"` 전체 통과.

## 3-7. 수정 후 테스트

- 개발자: 매처 3곳 교체, `sg docker -c ./gradlew test` 42개 전부 통과·BUILD SUCCESSFUL 보고. 디자이너: layout 닉네임 방어(`layout.html:22`), `AdminAccessTest` 통과.
- 아키텍트 재실행(`--rerun-tasks`)은 25분 걸린 뒤 `java.io.EOFException`으로 BUILD FAILED. 테스트 실패가 아니라 Gradle 테스트 워커/결과 파일 문제(첫 실행에서도 같은 증상, 리뷰어는 데몬 재사용 문제로 추정). 같은 시각 다른 팀원이 같은 build 디렉터리로 Gradle을 돌렸을 가능성이 있음(동시 실행 충돌 추정, 미확인). 이때 남은 XML 리포트는 42개 / 실패 0.
- 따라서 "통과"의 근거는 개발자 BUILD SUCCESSFUL + 리뷰어 재리뷰 실행. gradle은 한 번에 한 팀원만 돌린다.

## 3-8. 리뷰 통과

리뷰어 판정: 통과(반려 0). 42개 테스트 전부 통과(XML 근거), 리포트 이후 `src/` 변경 없음. 남은 권고(R2·R7·R8·V4 메모)는 SMTP 도입·2단계 후반으로 이월. 개발자에게 feat → test → docs 3커밋 지시(README `ADMIN_PASSWORD` 한 줄 포함 — 설계 §6, 현재 README에 없음).

## 3-9. 커밋 완료

1b2deca `feat(member)` (src/main 25파일) / e1aed40 `test(member)` (테스트 6파일) / 064ce99 `docs` (README 관리자 안내 + docs/ 8파일). push 안 함. 이후 HANDOFF 완료 표시를 `docs:` 커밋 1건으로 추가 예정.

## 4. 커밋 이력 주의

eb8c0c2는 메시지(`feat(member)` + `test(member)` 혼합, 변경은 4개 파일뿐)가 내용과 맞지 않는다. 히스토리 재작성은 하지 않고, member 파일은 새 커밋으로 나눠 올린다(feat → test → docs).

## 5. 진행 순서

1. [완료] 개발자: §2 수정 + design §8 테스트 작성.
2. [완료] `sg docker -c ./gradlew test` 42개 통과 (§3-7).
3. [완료] 리뷰어 정식 리뷰 통과 (§3-6, §3-8).
4. [완료] 개발자 커밋 3건: `1b2deca` feat(member), `e1aed40` test(member), `064ce99` docs (push 안 함).
5. [완료] HANDOFF 회원 항목 완료 표시 (`docs: HANDOFF 회원가입·로그인 완료 표시`).
