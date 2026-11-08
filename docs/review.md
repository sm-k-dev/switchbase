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

---

## 체감 평가 리뷰 (rating-design §3·§4·§5·§8, rating-screens)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 13개 스위트 85개 전부 통과(합계 failures 0, errors 0, 14:11 실행, `RatingTest` 14개). 재실행 없이 XML과 소스, 문서로 판단.

### 판정: 반려 (필수 1건, 문서 정합 1건, 권고 2건)

### 확인 통과
- (1) 권한: 수정은 `loadForEdit`이 작성자가 아니면 404(서비스). 삭제는 `delete`가 작성자 또는 관리자만 통과시키고 제3자는 `RatingAccessDeniedException`(`@ResponseStatus(FORBIDDEN)`)으로 403. 템플릿은 `mine`(수정 링크)과 `deletable`(삭제 폼)만 쓰고, 두 값은 `RatingService.view`가 서버에서 계산한다. 테스트 `deletableAndMineFlagsFollowAuthorAndAdmin`, `deletionAllowedForAuthorAndAdminButNotThirdParty`, `authorEditsWithConflictAndOthersGetNotFound`.
- (2) 유일성: V7의 `UNIQUE NULLS NOT DISTINCT (switch_id, member_id, build_id)`로 빌드 없는 평가도 스위치당 1건이 DB에서 보장된다. 서비스의 `findByKeyboardSwitchIdAndMemberIdAndBuildId(..., null)`는 Spring Data가 null을 `IS NULL`로 바꿔 같은 조건을 본다. 저장 시 유니크 위반은 `RatingDuplicateException`(409)로 바뀐다. 빌드 소유: `ownedBuild`가 `build.member == 요청자`를 확인하고, 남의 빌드·없는 빌드는 `BuildNotOwnedException`으로 폼 필드 오류. 테스트 `buildOfAnotherMemberCannotBeLinked`, `sameSwitchAndBuildCombinationIsRejectedOnceWithoutBuildToo`.
- (3) 표본 수: `count(distinct r.member.id)`. 테스트 `sampleCountIsDistinctMembersNotBuilds`.
- (4) 숨김 빌드: `RatingView`는 `buildLinked`(boolean)만 갖고 빌드 이름 필드가 없다. 목록 템플릿은 "빌드 있음"만 쓴다. 테스트 `hiddenBuildShowsOnlyAsBuildLinkedWithoutItsName`.
- (5) 이메일: `RatingView`는 `authorNickname`만 가진다. 템플릿에 email 표시 없음. 테스트 `memberRatingIsListedWithNicknameAndNoEmail`, `pagesRenderWith200AndNoEmail`. 자동완성 조각은 `th:text`와 `th:attr`로 이스케이프되고, 클릭은 `this.dataset.tag`로 넘겨 문자열 주입이 없다. 테스트 `tagsAreNormalizedAndSuggestionIsEscaped`. 태그 정규화(공백 정리, 소문자 키, 처음 입력한 표시 형태)도 맞다.
- `SecurityConfig`: 목록·요약은 공개, 등록·수정·삭제·자동완성 조각은 로그인 필요로 규칙이 있다. 상세의 `summary`·`list`는 비로그인에도 동작한다.

### 필수 수정

**R1. 개조 태그 동시 생성 시 500**
- `RatingService.resolveTags`가 `tags.findByNormalized(key)`로 없으면 `tags.save(new ModTag(...))`를 호출한다. `ModTag.id`는 `GenerationType.IDENTITY`라 `save`가 바로 INSERT한다. 두 회원이 같은 새 태그를 동시에 등록하면 두 번째 INSERT가 `uk_mod_tag_normalized` 위반으로 `DataIntegrityViolationException`을 던진다.
- 이 예외는 `saveRating`의 try 바깥(`resolveTags` 안)에서 나므로 `RatingController`의 catch에 걸리지 않는다. 결과는 **500**이다. 평가 등록과 수정 두 경로 모두 해당한다.
- 조치: `tags.save`를 try로 감싸고 `DataIntegrityViolationException`이면 `tags.findByNormalized(key)`로 다시 찾아 쓴다(또는 `ON CONFLICT`에 준하는 재조회). 재현 테스트 추가는 선택이지만, 최소한 동시성 없이 "기존 태그를 재조회"하는 단위 검사 1건을 권한다. 
- 참고: 이 예외를 `saveRating`의 catch로 옮기면 "이미 평가했어요"라는 잘못된 메시지가 나온다. 태그 경로에서 따로 처리해야 한다.

### 문서 정합 (아키텍트 결정)
- **수정 권한 응답 코드**: `rating-design.md` §8은 "다른 회원 수정 → 403"이라고 적었다. 구현과 `rating-screens.md` §1은 **404**다. 둘 중 하나로 맞출 것. 구현은 화면 문서를 따른 것으로 보인다. 404가 낫다는 판단이면 설계 §8을 고치면 된다.

### 권고 (수정 요청 아님)
- **평균 기준**: 요약의 평균은 `avg(...)`로 **평가 건수(행)** 기준이다. 표본 수는 **서로 다른 회원 수**다. 한 회원이 빌드 두 대로 평가하면 평균에 두 번 들어가는데 "N명"에는 한 명으로 세인다. 설계 §5.3은 평균 계산 방식을 명시하지 않았다. 회원별 평균을 먼저 낸 뒤 평균하거나, "N명"을 "N건"으로 바꿀지 정할 것.
- **LIKE 이스케이프**: 자동완성은 `findTop10ByNormalizedStartingWithOrderByNormalizedAsc`를 쓴다. 입력의 `%`·`_`가 와일드카드로 작동하는지 확인하지 않았다. 결과 개수만 바뀌고 주입은 아니므로 낮은 위험이다.
- 테스트 공백(참고): 수정 화면에서 빌드를 다른 기존 평가와 같은 조합으로 바꾸는 경로(`update`의 중복 검사)는 테스트 이름에 보이지 않는다. 있으면 좋고 없어도 DB 유니크가 막는다.

### 재리뷰 조건
R1을 고치고 XML에서 통과를 확인하면 통과 처리한다. 문서 정합 항목은 아키텍트 결정 후 맞춘다.

---

## 체감 평가 재리뷰 (필수 1·정합·권고 반영)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 13개 스위트 87개 전부 통과(합계 failures 0, errors 0, 14:16 실행, `RatingTest` 17개 중 신규·갱신 3건 확인). 재실행 없이 XML과 소스, 문서로 판단.

### 판정: 통과 (주석 정리 1건 권고)

### 필수 1: 태그 동시 생성 (확인)
- `ModTagWriter.insert`가 `@Transactional(REQUIRES_NEW)`로 `saveAndFlush`한다. 유니크 위반은 이 내부 트랜잭션만 롤백시키고, 평가를 저장하는 바깥 트랜잭션은 살아 있다. `ModTagWriter`는 별도 빈이라 프록시 호출이 맞게 동작한다(자기 호출 아님).
- `RatingService.createOrReuse`가 `DataIntegrityViolationException`을 잡고 `tags.findByNormalized(normalized)`로 같은 키를 다시 찾는다. 바깥 트랜잭션은 커밋 전 조회이므로 이미 커밋된 행을 본다. 못 찾는 드문 경우에만 `OptimisticLockingFailureException`을 던진다.
- 컨트롤러: `create`가 `OptimisticLockingFailureException`을 409로 재렌더한다. `update`도 같은 예외를 409로 처리한다(태그 경합이 낙관적 잠금 충돌과 같은 처리). 500 경로는 보이지 않는다.
- 테스트 `concurrentNewTagCreationDoesNotFail500`이 통과.

### 정합: 수정 타인 403 (확인)
- `loadForEdit`이 작성자가 아니면 `RatingAccessDeniedException`(403)을 던진다. 수정 화면 GET과 수정 POST 모두 이 경로를 쓴다.
- `rating-screens.md` §1과 `rating-design.md` §8이 모두 403으로 맞춰졌다. 테스트 `authorEditsWithConflictAndOthersGetForbidden` 통과.

### 권고: 요약 (확인)
- `RatingSummary`에 `ratingCount`(평가 건수)가 추가됐다. `sampleCount`는 서로 다른 회원 수다. 평균은 평가 건 기준이다. 쿼리는 `count(r)`과 `count(distinct r.member.id)`를 함께 낸다.
- 표본 수 정의는 설계와 일치한다. 테스트 `sampleCountIsDistinctMembersNotBuilds` 유지.
- LIKE 와일드카드: 테스트 `suggestionTreatsLikeWildcardsAsLiterals`가 `%`·`_`를 문자 그대로 취급함을 확인한다.

### 메모 (수정 요청 아님, 정리 권고)
- **주석 불일치 3곳**: 정합 결정 후 주석이 이전 404 기준으로 남아 있다.
  - `RatingService.java:29` 클래스 설명: "수정은 작성자만(아니면 404)" → 403으로 고칠 것.
  - `RatingService.java:121` `update` 설명: "작성자만(아니면 404)" → 403으로 고칠 것. 실제로 `update`는 `loadForEdit`을 쓰므로 403이다.
  - `RatingController.java:75` `editForm` 설명: "작성자가 아니면 404" → 403으로 고칠 것.
  - 코드 동작은 맞다. 주석만 맞추면 된다.
- **ratingCount 미표시**: `ratings/section.html`은 "평가 N명"만 보여 준다. `M건`을 쓰기로 했으면 템플릿에 추가하고, 쓰지 않을 거면 필드를 둘 이유를 정할 것. 현재는 데이터만 있다.
- **커넥션 사용**: `REQUIRES_NEW`는 태그 생성 순간 두 번째 DB 연결을 잡는다. 동시 등록이 많으면 커넥션 풀(Hikari 기본 10)에 부담이 될 수 있다. 지금 규모에서는 문제 없다. 트래픽이 늘면 태그 생성을 평가 저장 전에 짧게 끝내는 구조를 검토할 것.
- 태그 경합 메시지: 태그 경합이 `update`·`create` 모두에서 "그 사이 다른 사람이 먼저 고쳤어요" 문구로 나간다. 평가 경합에는 맞지만 태그 전용 문구가 아니다. 허용 범위다.

### 재리뷰 후속 확인 (체감 평가)
- `ratings/section.html` 9행에 `평가 N명 · M건`(sampleCount·ratingCount)이 반영되어 있음을 확인. 권고 항목 해소.
- 주석 404 3곳(`RatingService` 29·121행, `RatingController` 75행)은 아직 404로 남아 있음. 동작은 403이 맞으므로 주석만 고치면 됨. 개발자 반영 후 주석 확인 예정(통과 판정에는 영향 없음).

---

## 집계 캐시 리뷰 (stats-design §3·§5·§5-1·§7)

작성: 리뷰어 / 2026-11-08 / 근거: `build/test-results/test/*.xml` 14개 스위트 94개 전부 통과(합계 failures 0, errors 0, 15:01 실행, `RatingStatsTest` 7건 포함). 재실행 없이 XML과 소스, 문서로 판단.

### 판정: 통과 (운영 메모 2건)

### (1) 같은 트랜잭션 갱신과 동시 저장
- 호출 위치 3곳 모두 확인: `create`(저장 후 `recompute`), `update`(저장 후 `recompute`), `delete`(`delete` → `flush` → `recompute`). 셋 다 `RatingService`의 `@Transactional` 메서드 안이다. `RatingStatsService.recompute`는 기본 전파(REQUIRED)라 같은 트랜잭션에 합류하므로 평가 저장과 함께 커밋·롤백된다.
- 동시 저장: 각 쓰기는 "평가 쓰기 → 집계 행 잠금(`PESSIMISTIC_WRITE`) → 같은 트랜잭션에서 `summaryOf` 재계산" 순서다. 잠금을 쥔 쓰기는 자기 변경 전과 다른 쓰기 커밋분을 모두 본다. 마지막으로 잠금을 쥔 쓰기가 가장 나중에 재계산하므로 최종 행은 전체 집계와 같아진다. 테스트 `concurrentSavesBothAreReflectedInTheRow`.
- 데드락: 쓰기 트랜잭션은 집계 행 잠금을 하나만 잡고, 그 뒤 다른 집계 잠금을 기다리지 않는다. 평가 INSERT의 외래 키 공유 잠금(키 스위치·빌드·회원·태그 행)은 `UPDATE keyboard_switch`(NO KEY UPDATE)나 `UPDATE keyboard_build`(잠금 FOR UPDATE)와 대기 고리를 만들지 않는다. 확인한 범위에서 순환 대기는 찾지 못했다.

### (2) 행 없음과 요약 규칙
- `recompute`는 `ensureRow`(`INSERT … ON CONFLICT DO NOTHING`)로 행을 먼저 만들고, 그 뒤 `findForUpdate`로 잠근다. 동시에 만들어도 충돌은 무시되고, 잠금 뒤에는 커밋된 행을 본다. 행이 여전히 없으면 `IllegalStateException`인데, 정상 경로에서는 일어날 수 없다.
- 요약 읽기 `current`: 행이 없으면 평균 null, 건수 0. 행이 있는데 평가가 모두 삭제되면 `replace`가 평균을 null, 건수를 0으로 쓴다. 테스트 `switchWithoutRowShowsEmptySummary`, `lastRatingDeletedLeavesNullAveragesAndZeroCounts`.

### (3) V8 채우기
- `INSERT … SELECT`는 스위치별 `AVG(sound/fatigue/star)`, `COUNT(DISTINCT member_id)`, `COUNT(*)`를 낸다. `summaryOf`와 같은 식이다(평균은 평가 건 기준, 표본 수는 서로 다른 회원 수, 건수는 행 수).
- 평가가 없는 스위치는 행을 만들지 않는다. 읽는 쪽이 행 없음을 0과 null로 처리하므로 결과가 같다.
- NUMERIC(4,2)에 1~5 평균을 담으므로 자릿수는 충분하다. 실데이터 검증은 하지 않았다(요청대로).

### (4) 재계산 명령
- `POST /admin/stats/recompute`는 `SecurityConfig`의 `/admin/**` 규칙(ADMIN만)으로 막힌다. 비로그인은 로그인 화면, 회원은 403. 테스트 `recomputeIsAdminOnly`.
- CSRF: Spring Security 기본 CSRF가 켜져 있어 토큰 없는 POST는 거부된다. 테스트는 `csrf()`를 붙여 통과한다.

### 운영 메모 (수정 요청 아님)
- **관리자 버튼 없음**: 화면이 없어서 관리자는 CSRF 토큰을 포함해 직접 호출해야 한다. 설계는 "폼 버튼 또는 curl"이라고 했지만 템플릿에는 버튼이 없다. 필요하면 관리자 페이지에 폼 버튼을 추가할 것.
- **긴 트랜잭션**: `recomputeAll`은 모든 스위치를 한 트랜잭션에서 다시 계산하므로 잠금을 끝까지 쥔다. 그동안 그 스위치들에 대한 평가 저장이 기다린다. 지금 규모에서는 괜찮다. 데이터가 커지면 스위치 단위로 나눠 실행할 것.
