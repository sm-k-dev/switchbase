# 후기·컬렉션·신고·탈퇴 화면 계약 (2단계, 확정)

작성: 아키텍트 / 2026-11-08 / 기준: `docs/work/review-collection-report-design.md` §4·§5·§6·§7, 디자이너 초안 `docs/work/review-screens-draft.md`
상태: **확정**. 템플릿 이름과 모델 속성 이름은 디자이너 초안을 그대로 쓴다. 바꾸면 양쪽에 알린다.

## 0. 확정 결정 (아키텍트, 2026-11-08)
- 탈퇴 화면은 `templates/member/withdraw.html`에 둔다.
- 평가 숨김은 **작성자 본인 행에만** 숨김 표시와 해제 버튼을 보인다. 타인의 평가 목록에서는 숨김 평가를 **뺀다**(조회 쿼리에서 제외, 작성자 본인만 포함).
- 목록 조회 쿼리의 숨김 조건은 "숨김 평가는 작성자 본인만 포함" 방식이다(`hidden = false OR 작성자 = 본인`). 테스트로 고정한다.

## 1. 템플릿과 파일 (초안 그대로)

| 파일 | 용도 | 포함 위치 |
|---|---|---|
| `reviews/section.html` (fragment `section`) | 후기 목록·정렬·도움됨·신고 | `switches/detail` 하단 |
| `reviews/form.html` | 후기 쓰기·수정 | 단독 |
| `collections/toggles.html` (fragment `toggles`) | 즐겨찾기·보유·써봤음 토글 | `switches/detail` 상단 |
| `collections/me.html` | `/me/collection` 상태 탭, 공개 설정 | 단독 |
| `reports/button.html` (fragment `button`) | 신고 링크 | 후기·평가 항목 |
| `reports/form.html` | 신고 폼 | 단독 |
| `admin/reports.html` | 관리자 신고 목록과 숨김·기각 | 단독 |
| `member/withdraw.html` | 탈퇴 확인 | 단독 (확정) |
| `ratings/section.html` (기존) | 평가 목록. 작성자 본인 숨김 행에 "숨김" 표시와 해제 버튼 추가 | `switches/detail` |

## 2. 경로

| 화면 | 메서드·경로 | 로그인 | 비고 |
|---|---|---|---|
| 상세 후기 정렬 | GET `/switches/{id}?sort=recent\|helpful&reviewPage=` | 불필요 | `sort` 기본 `recent` |
| 후기 쓰기 폼 | GET `/switches/{id}/reviews/new` | 필요 | |
| 후기 저장 | POST `/switches/{id}/reviews` | 필요 | 성공 → 상세 |
| 후기 수정 폼 | GET `/reviews/{id}/edit` | 필요(작성자, 아니면 403) | |
| 후기 수정 저장 | POST `/reviews/{id}` | 필요(작성자) | 충돌 409 재렌더 |
| 후기 삭제 | POST `/reviews/{id}/delete` | 필요(작성자 또는 관리자) | 소프트 딜리트 |
| 도움됨 토글 | POST `/reviews/{id}/helpful` | 필요 | 본인 후기 제외 |
| 후기 숨김 해제 | POST `/reviews/{id}/unhide` | 필요(작성자만, 아니면 404) | |
| 평가 숨김 해제 | POST `/ratings/{id}/unhide` | 필요(작성자만, 아니면 404) | 신규 |
| 컬렉션 토글 | POST `/switches/{id}/collection/{FAVORITE\|OWNED\|TRIED}` | 필요 | 누르면 켜고 다시 누르면 끔 |
| 내 컬렉션 | GET `/me/collection?status=ALL\|FAVORITE\|OWNED\|TRIED&page=` | 필요 | 본인만 |
| 컬렉션 공개 설정 | POST `/me/collection/visibility` (`collectionPublic`) | 필요 | 한 번에 전체 |
| 타인의 컬렉션 | GET `/members/{id}/collection?status=ALL\|FAVORITE\|OWNED\|TRIED&page=` | 불필요 | 공개 설정이 꺼졌거나 탈퇴한 회원이면 404. 본인이면 항상 보임. 템플릿 `collections/public` (신규, 디자이너) |
| 신고 폼 | GET `/reports/new?targetType=&targetId=` | 필요 | |
| 신고 저장 | POST `/reports` | 필요 | 성공 → 대상 상세 |
| 관리자 신고 목록 | GET `/admin/reports?status=RECEIVED\|RESOLVED\|DISMISSED&page=` | ADMIN | |
| 관리자 숨김 처리 | POST `/admin/reports/{id}/hide` | ADMIN | 대상을 숨기고 신고를 RESOLVED로 |
| 관리자 기각 | POST `/admin/reports/{id}/dismiss` | ADMIN | 신고를 DISMISSED로 |
| 탈퇴 확인 | GET `/me/withdraw` | 필요 | `member/withdraw` |
| 탈퇴 | POST `/me/withdraw` (`confirmText`) | 필요 | `confirmText`가 "탈퇴"여야 한다. 성공 → 로그아웃, `/` |

- 요청 파라미터 이름은 그대로 쓴다. 정렬 값은 `recent`와 `helpful`만 허용한다(그 밖의 값은 `recent`로 본다).

## 3. 모델 속성 (초안 이름 그대로)

### 상세 `switches/detail` (기존 모델에 추가)
| 속성 | 타입 | 설명 |
|---|---|---|
| `sort` | String | `recent` 또는 `helpful` |
| `reviewPage` | `Page<ReviewView>` | 20건 |
| `collection` | `CollectionState` | 비로그인이면 null |
| `switchId` | Long | 조각이 쓴다 |

- 기존 `summary`, `ratingPage`, `message`는 그대로.

### `ReviewView`
`id`, `authorNickname`(탈퇴 회원은 "탈퇴한 회원"), `body`, `recommendedLabels`(List<String>), `helpfulCount`(int), `createdAt`(Instant), `mine`, `deletable`, `canVote`(로그인 회원이고 본인 글 아님), `helpfulByMe`(boolean, 버튼 문구용), `hidden`(작성자 본인에게만 true로 보인다)

### `CollectionState`
`favorite`, `owned`, `tried` (boolean)

### 후기 폼 `reviews/form`
- `form` (`ReviewForm`): `version`(long), `recommendedFor`(List<String>), `body`(2000자 이하)
- `switchId` (쓰기), `reviewId` (수정일 때만)
- `recommendOptions` (List<Option>): 추천 대상 고정 선택지

### 내 컬렉션 `collections/me`
- `status` (String): `ALL` 또는 상태 코드
- `items` (`Page<CollectionItemView>`): `switchId`, `switchName`, `statusLabel`, `createdAt`
- `collectionPublic` (boolean)
- `message` (flash)

### 신고 폼 `reports/form`
- `form` (`ReportForm`): `targetType`, `targetId`, `reasonCode`, `detail`(500자 이하)
- `targetLabel` (String): 예 "후기"
- `reasonOptions` (List<Option>): 고정 사유

### 관리자 신고 `admin/reports`
- `page` (`Page<ReportView>`): `id`, `createdAt`, `targetHref`(없으면 null → "대상 없음"), `targetLabel`, `reasonLabel`, `detail`, `reporterLabel`, `status`(코드), `resolutionLabel`
- `status` (String): 필터
- `message` (flash)

### 타인 컬렉션 `collections/public` (신규)
- `memberId` (Long), `status` (String), `items` (`Page<CollectionItemView>`: `switchId`, `switchName`, `statusLabel`, `createdAt`)
- 닉네임은 `memberId`로 화면에 보이지 않는다. 필요하면 서버가 `memberNickname`을 추가한다(요청 시).

### 탈퇴 `member/withdraw`
- `form` (`WithdrawForm`): `confirmText`

### 평가 뷰 `RatingView` (기존에 추가)
- `hidden` (boolean): 작성자 본인 행에만 true로 채운다. 타인에게는 숨김 행 자체가 목록에 없다.

## 4. 권한 표시 규칙 (템플릿이 판단하는 것)
- 수정 버튼: `r.mine`
- 삭제 버튼: `r.deletable` (`hasRole` 금지)
- 도움됨 버튼: `r.canVote`
- 숨김 해제 버튼: `r.mine and r.hidden`
- 컬렉션 토글: `collection` 존재 여부(비로그인 null)
- 신고 링크: 로그인 회원(`sec:authorize`) + 본인 글 아님(`!r.mine`)

## 5. 서버가 보장하는 것 (템플릿이 믿어도 되는 값)
- 숨김 평가·후기는 작성자 본인 조회에만 포함된다. 타인 목록 쿼리에서 빠진다.
- 삭제된 행은 어떤 목록에도 없다.
- 탈퇴 회원의 글은 보관 기간 안에서만 목록에 보이고, 작성자 닉네임은 "탈퇴한 회원"으로 나온다.
- 이메일은 어떤 모델 속성에도 없다.

## 6. 고정 선택지 코드와 표시 이름 (서버가 내려 주는 값, 개발자 확정)

| 항목 | 코드 | 표시 이름 |
|---|---|---|
| 추천 대상 (`recommendOptions`, 여러 개) | `BEGINNER` | 입문자 |
| | `QUIET` | 조용한 환경 |
| | `SMOOTH_LOVER` | 부드러운 타건을 좋아하는 사람 |
| | `TACTILE_LOVER` | 택타일을 좋아하는 사람 |
| 신고 사유 (`reasonOptions`) | `SPAM` | 광고·스팸 |
| | `ABUSE` | 욕설·비방 |
| | `WRONG_INFO` | 잘못된 정보 |
| | `OTHER` | 기타 |
| 컬렉션 상태 | `FAVORITE` | 즐겨찾기 |
| | `OWNED` | 보유 |
| | `TRIED` | 써봤음 |
| 신고 대상 | `REVIEW` / `RATING` / `BUILD` | 후기 / 평가 / 빌드 |

- 탈퇴 확인 문구는 `탈퇴`여야 한다(공백 제거 후 비교). 틀리면 같은 화면을 다시 그린다.
- 도움됨·숨김·기각·삭제 같은 버튼 결과는 모두 redirect와 flash `message`로 돌아간다(재렌더 없음). 충돌(409)과 검증 실패만 같은 폼을 다시 그린다.
