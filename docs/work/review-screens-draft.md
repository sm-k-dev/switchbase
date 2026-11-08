# 후기·컬렉션·신고 화면 초안 이름 (디자이너, 계약 확정 전)

작성: 디자이너 / 2026-11-08 / 기준: `docs/work/review-collection-report-design.md` §4·§6·§7
상태: `review-screens.md`가 없어서 템플릿이 쓰는 이름을 여기에 정리했다. 계약이 나오면 이 이름과 맞춘다.

## 1. 템플릿과 파일

| 파일 | 용도 | 포함 위치 |
|---|---|---|
| `reviews/section.html` (fragment `section`) | 후기 목록·도움됨·신고 버튼 | `switches/detail` 하단 |
| `reviews/form.html` | 후기 쓰기·수정 | 단독 화면 |
| `collections/toggles.html` (fragment `toggles`) | 즐겨찾기·보유·써봤음 토글 | `switches/detail` 상단 |
| `collections/me.html` | `/me/collection` 상태 탭, 공개 설정 | 단독 화면 |
| `reports/button.html` (fragment `button`) | 신고 링크 | 후기 항목 |
| `reports/form.html` | 신고 폼 | 단독 화면 |
| `admin/reports.html` | 관리자 신고 목록과 숨김·기각 | 단독 화면 |
| `member/withdraw.html` | 탈퇴 확인 | 단독 화면 (`member/`에 둠. 목록에 없어 확인 필요) |

## 2. 경로 (템플릿이 가정한 것)

| 화면 | 메서드·경로 | 비고 |
|---|---|---|
| 상세 후기 정렬 | GET `/switches/{id}?sort=recent|helpful&reviewPage=` | |
| 후기 쓰기 폼 | GET `/switches/{id}/reviews/new` | |
| 후기 저장 | POST `/switches/{id}/reviews` | |
| 후기 수정 폼 | GET `/reviews/{id}/edit` | |
| 후기 수정 저장 | POST `/reviews/{id}` | |
| 후기 삭제 | POST `/reviews/{id}/delete` | 작성자 또는 관리자 |
| 도움됨 토글 | POST `/reviews/{id}/helpful` | 본인 후기 제외 |
| 숨김 해제 | POST `/reviews/{id}/unhide` | 작성자만 |
| 컬렉션 토글 | POST `/switches/{id}/collection/{FAVORITE|OWNED|TRIED}` | 누르면 켜고 끔 |
| 내 컬렉션 | GET `/me/collection?status=ALL|FAVORITE|OWNED|TRIED&page=` | |
| 컬렉션 공개 설정 | POST `/me/collection/visibility` (`collectionPublic`) | 한 번에 전체 |
| 신고 폼 | GET `/reports/new?targetType=&targetId=` | |
| 신고 저장 | POST `/reports` | |
| 관리자 신고 목록 | GET `/admin/reports?status=RECEIVED|RESOLVED|DISMISSED&page=` | |
| 관리자 숨김 처리 | POST `/admin/reports/{id}/hide` | 대상을 숨김 |
| 관리자 기각 | POST `/admin/reports/{id}/dismiss` | |
| 탈퇴 확인 | GET `/me/withdraw` | |
| 탈퇴 | POST `/me/withdraw` (`confirmText`) | |

## 3. 모델 속성 (템플릿이 읽는 이름)

### 상세 (`switches/detail`에 추가)
- `sort` (String): `recent` 또는 `helpful`
- `reviewPage` (`Page<ReviewView>`)
- `collection` (`CollectionState`, 비로그인이면 null)
- `switchId` (Long): 조각이 쓰도록 `th:with`로 넘긴다

### `ReviewView`
- `id`, `authorNickname`(탈퇴 회원은 "탈퇴한 회원"), `body`, `recommendedLabels`(`List<String>`), `helpfulCount`(int), `createdAt`(Instant)
- `mine`(수정 버튼), `deletable`(삭제 버튼), `canVote`(도움됨 버튼 노출: 로그인 회원이고 본인 글 아님), `helpfulByMe`(버튼 문구)
- `hidden`(작성자에게 숨김 안내와 해제 버튼)

### `CollectionState`
- `favorite`, `owned`, `tried` (boolean)

### 후기 폼 `reviews/form`
- `form` (`ReviewForm`): `version`(long), `recommendedFor`(`List<String>`), `body`(2000자)
- `switchId` (쓰기), `reviewId` (수정일 때만)
- `recommendOptions` (`List<Option>`): 추천 대상 고정 선택지

### 내 컬렉션 `collections/me`
- `status` (String), `items` (`Page<CollectionItemView>`: `switchId`, `switchName`, `statusLabel`, `createdAt`)
- `collectionPublic` (boolean)
- `message` (flash)

### 신고 폼 `reports/form`
- `form` (`ReportForm`): `targetType`, `targetId`, `reasonCode`, `detail`(500자)
- `targetLabel` (String): 예 "후기"
- `reasonOptions` (`List<Option>`): 고정 사유

### 관리자 신고 `admin/reports`
- `page` (`Page<ReportView>`): `id`, `createdAt`, `targetHref`(없으면 null → "대상 없음"), `targetLabel`, `reasonLabel`, `detail`, `reporterLabel`, `status`(코드: RECEIVED 등, 처리 버튼은 RECEIVED에서만), `resolutionLabel`
- `status` (String): 필터
- `message` (flash)

### 탈퇴 `member/withdraw`
- `form` (`WithdrawForm`): `confirmText`

## 4. 권한 표시 규칙 (템플릿이 판단하는 것)

- 수정 버튼: `r.mine`
- 삭제 버튼: `r.deletable` (`hasRole` 금지)
- 도움됨 버튼: `r.canVote`
- 숨김 해제 버튼: `r.mine and r.hidden`
- 컬렉션 토글: `collection` 존재 여부(비로그인 null)
- 신고 링크: 로그인 회원(`sec:authorize`) + 본인 글 아님(`!r.mine`)

## 5. 확인 요청

1. 탈퇴 화면을 `member/`에 둔 것이 맞는지(목록은 `reviews/`, `collections/`, `reports/`, `admin/`).
2. 평가 목록(`ratings/section`)에는 숨김 표시를 넣지 않았다. 평가 숨김을 작성자에게 표시할지 정해 주세요.
3. 후기 목록 정렬의 파라미터 이름(`sort`, `reviewPage`)과 값(`recent`, `helpful`)을 컨트롤러가 그대로 써 주세요.
