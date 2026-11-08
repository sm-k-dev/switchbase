# 코드 리뷰: member (회원가입·로그인)

작성: 리뷰어 / 2026-11-08

## 판정: 통과

- 1차 정식 리뷰에서 반려 4건(layout nickname이 AdminAccessTest를 깨뜨림 1, MockMvc `redirectedUrlPattern` 매처 3)을 냈고, 모두 수정 확인.
- 테스트: 9개 스위트 합계 42개, 실패 0·오류 0·건너뜀 0 (`build/test-results/test/*.xml`, 개발자가 `sg docker -c "./gradlew test"`로 실행). 리포트 이후 `src/` 변경 없음.
- 보안: 미인증 차단은 비밀번호 확인 뒤, 토큰은 해시 저장·24시간·1회용, CSRF 유지, Referrer-Policy no-referrer, 운영 로그 노출은 `@Profile("!prod")`로 차단(prod 기동 실패는 사용자가 의도로 확인).
- 남은 권고(반려 아님): `GET /verify`의 상태 변경, 토큰 동시 소비 경합, 만료 토큰 정리, V4 참고 사항 — `docs/work/member-review.md` 참고.
- 상세: `docs/work/member-review.md` (사전 리뷰 + 정식 리뷰 섹션)

---

## 0단계 (이메일 비공개) 짧은 리뷰

작성: 리뷰어 / 2026-11-08 / 범위: V5, `SwitchRevision.getEditorDisplay`, create·update·CSV의 닉네임·회원 id 기록, `MemberUserDetails` id, `switches/detail.html` 39행. 코드 읽기만 함(Gradle 미실행).

### 판정: 조건부 통과 (fix(member) 커밋 가능. 단, 아래 확인 1건 후)

확인된 것
- V5: `editor_member_id BIGINT REFERENCES member(id) ON DELETE SET NULL` 추가만 함. 기존 V1~V4 수정 없음. 타입이 `member.id BIGSERIAL`과 맞음.
- `SwitchRevision`: 화면용 getter는 `getEditorDisplay()`뿐이다. `getEditor()`가 없어 이메일이 템플릿이나 JSON으로 새어 나갈 경로가 없다. `member id == null`이면 "초기 관리자"로 표시.
- detail.html 39행이 `${r.editorDisplay}`로 바뀌었고, 다른 `r.editor` 참조는 템플릿에 없음.
- create·update·CSV 모두 `editorMemberId`와 닉네임을 넘긴다. 컨트롤러는 `@AuthenticationPrincipal MemberUserDetails`로 받는다. `MemberUserDetails`에 `getId()` 추가됨.
- 기존 호출부 누락 없음(main·test 전부 새 시그니처로 바뀜).

확인 필요 (커밋 전 1건)
1. `SwitchEditorDisplayTest`(신규, untracked)는 아직 실행 결과가 없다. 42개 스위트에 포함되지 않았다. 개발자가 `sg docker -c "./gradlew test"`로 돌려 XML에서 이 스위트가 통과하는 것을 확인한 뒤 커밋할 것.

비차단 메모 (수정 요청 아님)
- `@AuthenticationPrincipal MemberUserDetails`는 MemberUserDetails가 아닌 principal이면 null이 되어 NPE가 난다. 현재 `/admin/**`은 ADMIN만 들어오므로 실제로는 발생하지 않는다.
- `ON DELETE SET NULL`로 회원이 지워지면 그 사람의 편집도 "초기 관리자"로 바뀐다. 회원 탈퇴(2단계 후반)를 만들 때 "탈퇴한 회원" 표시를 따로 검토할 것.
- 기존 행의 `editor` 칸에는 관리자 이메일이 그대로 남는다. 화면에는 나오지 않으므로 설계(wiki-design §3)에는 맞지만, DB 덤프 시 노출될 수 있다는 점은 알고 있을 것.
- 컨트롤러 import 순서가 어긋나 있다(`com.switchbase.member`와 `AuthenticationPrincipal`이 Spring import 사이에 끼어 있음). 커밋 전에 정리하면 좋다.

### 0단계 추가 확인 (개발자 요청, 이메일 비노출 중심)
- XML 확인(`build/test-results/test/`): SwitchEditorDisplayTest 3/3, SwitchServiceTest 7/7, AdminAccessTest 3/3, failures·errors 0.
- **전체 45개는 XML로 확인 못 함.** 현재 폴더에 XML이 3개뿐이라, 마지막 실행이 부분 실행(`--tests`)이었던 것으로 보인다. 나머지 39개 스위트의 XML이 없다. 개발자가 `sg docker -c "./gradlew test"` 전체 실행 후 XML 9개 이상이 생겼는지 확인하고 커밋할 것.
- 이메일 비노출: `getEditor()` 같은 이메일 노출 getter 없음. 템플릿과 컨트롤러에서 `editor`를 직접 쓰는 곳 없음(`editorDisplay`·`editorNickname`·`editorMemberId`만 사용).
- 판정은 위 "조건부 통과" 그대로. 전체 XML 확인이 조건.

---

## 1단계 위키 리뷰 (수정·이력·되돌리기·최근 변경)

작성: 리뷰어 / 2026-11-08 / 기준: `wiki-design.md` §4·§5·§8, `wiki-screens.md`. 판단 근거: `build/test-results/test/*.xml` 11개 스위트 56개 전부 통과(failures·errors·skipped 0, 13:05 실행). Gradle은 실행하지 않음.

### 판정: 반려 (필수 2건, 권고 1건)

### 확인 통과
- (1) 권한 서버 검사: `SwitchService.revertBlock`이 권한·최신·첫 리비전을 검사하고, `revert`가 같은 검사를 다시 한다. 화면 숨김에 기대지 않음. 작성자 판별은 `getEditorMemberId()`와 `memberId` 비교(닉네임 아님). 관리자는 `member.isAdmin()`로 우회. `SecurityConfig`에서 수정·되돌리기 GET/POST가 `authenticated()`로 잠겨 있다.
- (2) 최신 아님·첫 등록 거부: `NOT_LATEST`, `FIRST_REVISION`이 서비스에서 판정되고 테스트가 있다(`notLatestRevisionCannotBeReverted`, `firstRevisionCannotBeReverted`).
- (3) 이메일 노출: `history.html`·`changes/list.html`·`detail.html`은 `editorDisplay`만 쓴다. `compare.html`·`revert.html`은 편집자 칸을 쓰지 않는다. `SwitchRevision`에 이메일 getter가 없다. 누출 경로는 찾지 못했다.
- 관리자 수정 경로(`/admin/switches/{id}` 수정·POST)는 제거됐다.
- `uk_switch_revision_no UNIQUE (switch_id, revision_no)`(V2)는 존재한다.

### 필수 수정
**R1. 낙관적 잠금이 '최신 리비전' 판정을 보호하지 못한다 (경합)**
- `KeyboardSwitch.version`(`@Version`)은 스펙이 실제로 바뀔 때만 올라간다고 보인다(Hibernate 더티 체크 기준, 실행으로 재현은 못 함). 값이 같은 수정(no-op)이나 직전과 같은 스펙으로 되돌리는 경우에는 리비전만 늘고 version은 그대로다.
- 그 결과: (a) A가 #3 되돌리기 화면을 연 뒤 B가 값이 같은 수정(#4)을 저장하면, A의 `expectedVersion`은 맞은 채로 남는다. 두 요청이 겹치면 A는 #4를 모른 채 되돌린다. (b) 같은 version의 되돌리기 두 건이 스펙 변화 없이 들어오면 둘 다 통과하고, 두 번째 저장은 `uk_switch_revision_no` 위반으로 **500**이 된다(충돌 문구가 아님).
- 조치: `update`·`revert` 시작에서 스위치 행을 잠근다(`PESSIMISTIC_WRITE`) 후 최신·권한을 검사하거나, 리비전 추가 때마다 version을 강제로 올린다. `DataIntegrityViolationException`(revision_no)은 `OptimisticLockingFailureException`으로 바꿔 충돌 문구로 보낸다. 재현 테스트: (a) no-op 수정 후 stale 되돌리기 → 거부, (b) 같은 version 되돌리기 두 건 → 한 건만 통과, 나머지는 500이 아닌 충돌 응답. 설계 §9가 "리뷰에서 확인"으로 둔 항목이 아직 테스트되지 않았다.

**R2. 이메일 비노출 테스트가 상세 화면에만 있다**
- 설계 §8은 "상세·이력·최근 변경 응답 본문에 로그인 이메일이 없음"을 요구한다. 명시적으로 검사하는 것은 `SwitchEditorDisplayTest`의 상세 화면뿐이다. `WikiRevisionTest`에는 이메일 부재 검사가 없다.
- 조치: 이력(`/history`), 최근 변경(`/changes`), 비교(`/compare`), 되돌리기 확인(`/revert`) 응답에 회원 이메일이 없음을 검사하는 테스트를 추가한다. 현재 누출 경로는 없지만 템플릿을 고칠 때 막는 안전장치가 된다.

### 권고 (반려 사유 아님)
- **D1. 되돌리기 POST 거부가 평문 403/409다 (UX).** `RevertNotAllowedException` 핸들러가 본문에 한 줄 텍스트만 돌려준다. 확인 버튼을 누른 뒤 "뒤에 다른 수정이 있어서…"가 빈 화면에 뜬다. NOT_LATEST는 두 사람이 겹칠 때 흔히 일어나므로 UX 문제가 크다. 권장: 되돌리기 화면을 `denyReason`과 함께 다시 그리거나(409 유지), 이력 화면으로 돌아가 플래시 메시지로 안내. 403(NOT_ALLOWED)은 화면에서 버튼을 숨기므로 평문이어도 괜찮다.

### 메모
- 11개 스위트 56개 통과는 XML로 확인했다. 다만 R1은 현재 테스트로 재현되지 않으므로, 통과 결과가 이 경합을 막는다는 뜻은 아니다.
- 설계 §8의 "비교 설명 전후 표시"와 "최근 변경 페이징"은 `WikiRevisionTest`에 있다(`compareMarksOnlyChangedFields`, `recentChangesAreNewestFirstAndPagedBy20`).

---

## 1단계 재리뷰 (반려 R1·R2·D1 수정 반영)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 11개 스위트 59개 전부 통과(failures·errors·skipped 0, 13:08 실행). 재실행 없이 XML과 소스로 판단.

### 판정: 통과

### R1 잠금 (확인)
- 같은 행 직렬화: `KeyboardSwitchRepository.findForUpdate`는 `PESSIMISTIC_WRITE`로 `keyboard_switch` 한 행을 잠근다. `update`와 `revert` 모두 `lockForUpdate(id)`를 쓰므로 같은 스위치의 두 요청은 한 줄로 선다. 두 메서드 모두 `@Transactional`이라 잠금은 커밋까지 유지된다.
- 잠금 안의 검사: `revert`는 잠금 → `revertBlock`(최신·첫 리비전·권한) → 버전 검사 순서다. `update`는 잠금 → 버전 검사다. 뒤 요청은 앞 요청이 커밋한 뒤에야 최신 리비전을 읽으므로, 옛 화면에서 온 되돌리기는 NOT_LATEST로 막힌다.
- 중복 리비전 번호: `saveRevision`이 `DataIntegrityViolationException`을 `OptimisticLockingFailureException`으로 바꾼다. 컨트롤러가 이를 잡아 409로 보낸다. 500 경로는 보이지 않는다.
- 교착: 잠금 대상이 스위치 행 하나뿐이라 순환 대기가 없다.
- 재현 테스트: `noOpEditThenStaleRevertIsRejected`(no-op 뒤 옛 화면 되돌리기 → 409, 리비전 3개 유지)와 `concurrentRevertsWithSameVersionLetOnlyOneThrough`(같은 version 두 건 → {302, 409}, 500 없음). 통과.
- 테스트의 한계(권고): 두 번째 테스트는 잠금이 없어도 `@Version` 충돌이나 NOT_LATEST로 409가 나와서, 잠금 자체를 끄면 실패하는지는 증명하지 못한다. 회귀 방지 목적으로는 충분하다. 잠금의 효과를 직접 보려면 잠금을 뺐을 때 실패하는 테스트가 필요하다(선택).

### R2 이메일 부재 (확인)
- `wikiPagesNeverShowMemberEmail`이 이력·최근 변경·비교·되돌리기 확인(리비전 1·2)·되돌리기 POST 거부 응답에서 회원 이메일 부재를 검사한다. 템플릿에 편집자 이메일 경로는 없다(앞 리뷰와 동일).

### D1 되돌리기 거부 응답 (확인)
- NOT_LATEST·FIRST_REVISION: `switches/revert` 화면을 사유(`denyReason`)와 함께 409로 다시 그린다. `revert.html`의 `th:unless="${canRevert}"`가 사유를 표시한다.
- NOT_ALLOWED: 403 평문(핸들러). 화면에서는 버튼을 숨기므로 괜찮다.
- 되돌리기 중 버전 충돌: 같은 화면에 "그 사이 다른 사람이 먼저 고쳤어요" 전역 오류와 409.

### 편집 충돌 409 (확인)
- `WikiController.update`는 충돌 시 `binding.reject`로 전역 오류를 넣고, `switches/edit`를 입력값과 함께 다시 그리며 409를 준다. `edit.html`이 `admin/switch-form-fields :: fields`를 포함하고, 그 조각에 `hasGlobalErrors` 표시가 있으므로 화면과 맞는다.

### 메모 (수정 요청 아님)
- 충돌 뒤 다시 그린 폼은 옛 `version`을 그대로 담는다. 문구("새로고침 후 다시 해 주세요")대로 새로고침해야 저장된다. 입력한 내용은 살아 있으므로 UX는 허용 범위다. 원하면 서버가 최신 version을 넣고 사용자가 내용만 확인하게 하는 방법도 있다(설계 판단).
- `revertDenied` 핸들러는 이제 NOT_ALLOWED만 받는다. 나머지 케이스가 늘어나면 핸들러의 상태 매핑을 다시 확인할 것.

---

## 빌드 등록 리뷰 (build-design §4·§8, build-screens)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 12개 스위트 69개 전부 통과(failures·errors·skipped 0, 13:27 실행). 재실행 없이 XML과 소스로 판단.

### 판정: 반려 (필수 2건)

### 확인 통과
- (1) 20개 제한: `create`가 `lockMember(memberId)`(PESSIMISTIC_WRITE)로 회원 행을 잠근 뒤 `countByMemberId`로 센다. 이 개수는 숨김 포함이다. 같은 회원의 등록은 직렬화된다. 테스트 `twentyBuildsLimitCountsHiddenToo`.
- (3) 이메일: `BuildView`에 이메일 필드가 없고 템플릿은 `ownerNickname`만 쓴다. 목록·상세·폼 전부 `everyBuildPageRendersWith200AndNoEmail`로 확인. 작성자 표시는 닉네임뿐.
- (4) 고정 선택지: `BuildPart.accepts`가 null(미지정)과 목록 안 코드만 허용하고, 범위 밖 값은 `BuildChoiceException`으로 해당 필드에 거부된다. "기타" 코드는 목록에 있어 허용. 빈 값·공백은 `blankToNull`로 NULL 처리, 이름 비움은 화면에 "내 키보드"(테스트 `blankNameIsStoredAsNullAndShownAsMyKeyboard`). 범위 테스트 `choicesOutsideFixedListAreRejectedAndOtherIsAllowed`.
- (5) 낙관적 잠금: `update`는 `findForUpdate` 잠금 뒤 `version`을 검사하고, 충돌은 `OptimisticLockingFailureException` → 409와 같은 폼 재렌더(`rerenderForm`). `switches/edit`과 같은 패턴. 테스트 `staleVersionRerendersFormWith409`.
- (2) 수정·숨김 권한: Q1대로 로그인 여부만 본다. `SecurityConfig`가 `GET /builds/new`, `GET /builds/*/edit`, `POST /builds`, `POST /builds/*`, `POST /builds/*/hide|unhide`를 `authenticated()`로 잠갔다. 비로그인은 로그인 화면(`anonymousWritesAreSentToLogin`). 숨김 빌드 상세는 `view()`에서 작성자 외에 404(아래 R2 제외).

### 필수 수정

**R1. 숨긴 뒤 리다이렉트가 숨긴 사람에게 404가 된다**
- `hide`·`update`는 `/builds/{id}`로 리다이렉트한다(build-screens §2와 일치). 그런데 상세는 숨김 빌드를 작성자 외에 404로 준다. 다른 회원이 빌드를 숨기면 리다이렉트 직후 그 회원은 404를 보고, 플래시 "숨겼어요"도 보지 못한다. 다른 회원이 숨김 빌드를 수정해도 같은 일이 생긴다.
- 테스트 `hideRemovesFromListAndUnhideRestores`는 리다이렉트 위치만 검사하고 따라가지 않는다. 그래서 이 문제를 잡지 못했다.
- 조치(아키텍트 결정 필요): 작성자가 아니면 `/builds`(목록)로 보내고 플래시 메시지를 남기거나, 설계를 바꿔 숨김을 건 사람도 상세를 볼 수 있게 한다. 테스트는 리다이렉트 뒤 `GET`까지 따라가 200(또는 목록 200)을 확인하도록 보강.

**R2. 숨김 빌드의 수정 화면이 숨김을 무시하고 내용을 보여 준다**
- `BuildController.editForm`은 `buildService.load(id)`를 쓴다. `view()`와 달리 숨김 여부와 작성자를 보지 않는다. 그래서 로그인한 다른 회원이 `GET /builds/{id}/edit`으로 숨김 빌드의 이름·메모·부품 값을 모두 볼 수 있다. 설계 §4("숨김 빌드 상세: 다른 회원 404")의 취지에 어긋나고, 숨긴 내용을 사실상 공개한다.
- 테스트에 이 경로(숨김 빌드 편집 화면을 다른 회원이 여는 경우)가 없어서 잡히지 않았다.
- 조치(아키텍트 결정 필요): (a) 숨김 빌드의 편집 화면과 저장은 작성자만(다른 회원은 404). 설계 §4 문구와 맞지만 Q1("누구나 수정")과 부분적으로 충돌하므로 Q1 해석을 확인할 것. (b) 다른 회원도 편집하게 하되 숨김 빌드의 내용을 편집 폼에 보여 주지 않는다. 실제로는 편집 자체가 불가능해지므로 (a)와 같다. 어느 쪽이든 `editForm`에 숨김 검사를 넣고, 숨김 빌드 편집 GET·POST에 대한 테스트를 추가한다.

### 권고 (수정 요청 아님)
- 숨김 해제·숨김 같은 상태 변경은 `setHidden`이 잠금 없이 호출되는 것이 아니라 잠금을 쓴다(`findForUpdate`). 문제 없음.
- 목록의 `countQuery`·`join fetch`로 N+1은 막혀 있다. 확인 완료.
- 테스트 목록(§8)의 "숨김 빌드 상세: 작성자 200, 다른 회원·비로그인 404"는 `hideRemovesFromListAndUnhideRestores` 안에 일부만 있다. 작성자 200과 다른 회원 404를 명시적으로 나눠 두면 좋다.

### 재리뷰 조건
R1·R2를 결정(아키텍트)한 뒤 수정하고, 위 테스트를 추가해 XML에서 통과를 확인하면 통과 처리한다.

---

## 빌드 재리뷰 (반려 R1·R2 수정 반영)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 12개 스위트 70개 전부 통과(합계 failures 0, errors 0, 13:30 실행). 재실행 없이 XML과 소스로 판단.

### 판정: 통과

### R2 숨김 빌드의 편집·수정 (확인)
- 편집 GET: `editForm`이 `buildService.view(id, viewerId)`를 쓴다. `view`는 숨김이고 작성자가 아니면 `NoSuchElementException`(404)이므로, 다른 회원에게 폼 값(이름·메모·부품)이 전혀 나가지 않는다.
- 수정 POST: `BuildService.update`가 잠금(`findForUpdate`) 뒤에 `requireVisibleTo`로 검사한다. 버전 검사보다 먼저 이뤄지므로 다른 회원은 숨김 빌드를 덮어쓸 수 없고, 그 전에 404가 난다. 회원 간 덮어쓰기 경로는 닫혔다.
- 공개 빌드는 Q1대로 로그인 회원 누구나 수정할 수 있다(`requireVisibleTo`가 공개 빌드는 통과시킴).
- 테스트 `otherMemberGetsNotFoundForHiddenBuildEditAndUpdate`: 다른 회원의 편집 GET·수정 POST → 404, 이름이 그대로 남음 확인, 작성자의 편집 GET → 200. 이 테스트가 R2를 직접 덮는다.

### R1 리다이렉트 목적지 (확인)
- 숨김(hide): `/builds`(목록). 목록은 숨김을 빼므로 숨긴 사람도 404를 보지 않는다. 플래시 "숨겼어요."는 목록 템플릿이 `message`로 표시한다.
- 숨김 해제(unhide): `/builds/{id}`. 해제 직후 빌드는 공개이므로 누구에게나 200이다.
- 수정(update): `/builds/{id}`. 수정이 성공하려면 viewer가 공개 빌드를 볼 수 있거나 작성자여야 하므로, 리다이렉트 시점에 상세는 200이다. 숨김 빌드를 작성자가 수정하면 작성자에게 보인다.
- 따라서 숨김 상태에서 404가 되는 리다이렉트는 없다. 테스트 `hideRemovesFromListAndUnhideRestores`는 hide 후 목록으로 간다는 점이 바뀌었다. 리다이렉트 목적지를 따라가는 검사는 여전히 없지만, 목적지 자체가 위 조건을 만족하므로 결과에는 영향이 없다.

### 확인 메모 (수정 요청 아님)
- 설계 문서 `build-screens.md` §2는 숨김 성공 → `/builds/{id}`로 적혀 있다. 지금 구현은 `/builds`다. 화면 문서를 맞춰 고칠 것(문서 불일치).
- 숨김 버튼은 로그인 회원 누구나에게 보인다(Q1). 작성자가 아닌 회원이 숨긴 빌드는 숨긴 사람에게도 상세가 404로 보인다는 점은 설계상 의도다. 숨김 상세를 본 적 없는 사람이 숨기는 것이므로 화면상 문제 없음.
- 테스트 수: BuildTest 11개(신규 2건 포함) 중 실패 0. 전체 70개 XML 합계 확인.

---

## 빌드 숨김 해제 작성자 전용 재리뷰

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 12개 스위트 71개 전부 통과(합계 failures 0, errors 0, 13:36 실행). 재실행 없이 XML과 소스, 문서(build-design §4·§10, build-screens §1·§4)로 판단.

### 판정: 통과 (문서 표현 1건 정리 권고)

### 코드 확인
- `BuildService.unhide(id, viewerId)`: 잠금(`lockForUpdate`) → `requireVisibleTo(build, viewerId)` → 숨김이면 해제. `requireVisibleTo`는 숨김 빌드에 대해 작성자가 아니면 `NoSuchElementException`(404)을 던진다. 관리자는 작성자가 아니므로 같은 404다.
- 공개 빌드에 대한 해제 요청: `requireVisibleTo`가 통과하고 `if (build.isHidden())`이 거짓이라 상태를 바꾸지 않는다. 버전도 오르지 않는다(변경 없음). 무해하다.
- `BuildController.unhide`: `viewerId(viewer)`를 넘기고, 성공 시 `/builds/{id}`(flash "다시 공개했어요.")로 보낸다. 해제 뒤 빌드는 공개이므로 상세는 200이다.
- `hide`는 권한 변경 없음(Q1, 로그인 회원 누구나). 목적지 `/builds`는 그대로다.

### 테스트 확인
- `onlyOwnerCanUnhideAndOthersGetNotFound`: 다른 회원과 **관리자 세션**의 해제 POST → 404, 상태 유지(`isHidden` true 확인), 작성자 해제 → 상세 리다이렉트, 상세 200. 관리자 포함 404가 테스트로 확인된다.
- `hideRemovesFromListAndUnhideRestores`: 숨긴 사람(다른 회원)의 해제 → 404, 작성자 해제 → 리다이렉트와 flash, 목록 복귀를 확인한다.
- `otherMemberGetsNotFoundForHiddenBuildEditAndUpdate`(이전 리뷰)와 함께, 다른 회원·관리자는 숨김 빌드의 조회·편집 GET·수정 POST·해제 모두 404다.

### 문서와 코드 일치
- build-design §4, 숨김 해제 행: "작성자만(사용자 확정). 관리자 포함 불가, 그 외 404". 구현과 일치한다.
- build-screens §1, 숨김 해제 행: "필요 (작성자만)", 성공 → `/builds/{id}`. 일치한다.
- build-screens §4, 해제 버튼: 작성자이면서 `hidden`일 때만 보인다. 일치한다.
- **문서 표현 정리 권고**: "그 외 404"는 숨김 빌드에 한정된다. 공개 빌드에 대한 해제 요청은 404가 아니라 상태 변화 없이 상세로 보내진다(무해). 설계 §4 해당 행에 "숨김 빌드에 한해"를 붙이면 문서와 코드의 차이가 없어진다.

### 메모 (수정 요청 아님)
- 작성자가 아닌 회원이 숨긴 빌드는 숨긴 사람도 해제할 수 없고, 작성자만 풀 수 있다. 이것이 확정 사항이며 §10에 기록되어 있다.
