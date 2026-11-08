# 체감 평가 화면 계약 (2단계 체감 평가)

작성: 아키텍트 / 2026-11-08 / 기준: `docs/work/rating-design.md` §4·§5·§7, 디자이너 초안(ratings/*)
목적: 개발자(컨트롤러·모델)와 디자이너(템플릿)가 **경로, 템플릿, 모델 속성 이름**을 같게 쓴다.

## 1. 경로와 템플릿

| 화면 | 메서드·경로 | 로그인 | 템플릿 | 비고 |
|---|---|---|---|---|
| 상세(기존) | GET `/switches/{id}` | 불필요 | `switches/detail` | 하단에 `ratings/section` include |
| 평가 등록 폼 | GET `/switches/{id}/ratings/new` | 필요 | `ratings/form` | |
| 평가 등록 저장 | POST `/switches/{id}/ratings` | 필요 | (실패 시 `ratings/form`) | 성공 → `/switches/{id}`, flash `message` |
| 평가 수정 폼 | GET `/ratings/{id}/edit` | 필요(작성자만) | `ratings/form` | 작성자가 아니면 403 (평가는 공개 데이터라 존재를 숨기지 않음) |
| 평가 수정 저장 | POST `/ratings/{id}` | 필요(작성자만) | (실패·충돌 시 `ratings/form`, 충돌 409) | 성공 → `/switches/{id}` |
| 평가 삭제 | POST `/ratings/{id}/delete` | 필요(작성자 또는 관리자) | — | 성공 → `/switches/{id}`, flash "삭제했어요." |
| 태그 자동완성 | GET `/mod-tags/suggest?modTags=` | 필요 | `ratings/tag-suggest` | htmx 조각. 마지막 토큰 기준 |

- 평가 등록 폼의 "빌드 없음"은 `buildId=null`로 보낸다.
- 평가 목록·요약은 상세 화면의 일부이며 비로그인도 본다.

### 삭제 권한 (확정, 사용자 재확정)
- **작성자 본인과 관리자 모두 삭제할 수 있다.** 수정은 작성자만.
- 삭제 버튼 표시는 **서버가 계산한 `RatingView.deletable`**만 본다. 템플릿에서 `hasRole('ADMIN')`으로 판단하지 않는다(디자이너 초안의 `section.html` 41행은 이 규칙으로 고친다).
- 수정 버튼 표시는 `RatingView.mine`.

## 2. 모델 속성

### 평가 폼 `ratings/form`
| 속성 | 타입 | 설명 |
|---|---|---|
| `form` | `RatingForm` | 필드: `version`(long), `buildId`(Long, null=빌드 없음), `soundLevel`(1~5), `soundTone`, `feel`, `fatigueLevel`(1~5), `starRating`(1~5), `lubed`(Boolean, null=미입력), `modTags`(쉼표 문자열) |
| `switchId` | `Long` | 폼 action과 취소 링크 |
| `ratingId` | `Long` | 수정일 때만. 등록이면 null |
| `buildOptions` | `List<Option>` | 본인 빌드 목록 + 첫 항목 "빌드 없음"(값 null) |
| `soundToneOptions` | `List<Option>` | CLACKY(도각), POPPY(쪅), HEAVY(묵직), DULL(둔탁) |
| `feelOptions` | `List<Option>` | SMOOTH(부드러움), CRISP(명확함), CATCHY(걸림) |

- `Option(code, label)`은 build-screens의 `Option`과 같은 타입을 쓴다.
- 충돌(409)과 검증 실패는 같은 폼을 다시 그린다. 전역 오류는 `BindingResult` 전역 오류.

### 상세 `switches/detail` (기존 모델에 추가)
| 속성 | 타입 | 설명 |
|---|---|---|
| `message` | `String` | flash |
| `summary` | `RatingSummary` | `avgSound`, `avgFatigue`, `avgStar`(Double, 평가가 없으면 null), `sampleCount`(서로 다른 회원 수, 표본 수), `ratingCount`(평가 건수, 평균의 기준) |
| `ratingPage` | `Page<RatingView>` | 최신순 20건. 페이징 파라미터 이름 `ratingPage` |

### 평가 목록 `ratings/section` (상세에서 include)
- 위 `summary`, `ratingPage`, `switchId`를 쓴다.

### 뷰 타입 `RatingView`
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | |
| `authorNickname` | String | 닉네임만. 이메일 금지 |
| `starRating` | int | |
| `soundLevel` | int | |
| `soundTone` | String | 표시명 (CLACKY → 도각) |
| `feel` | String | 표시명 |
| `fatigueLevel` | int | |
| `lubedLabel` | String | "예"/"아니오", 미입력이면 null → 화면에서 숨김 |
| `modTags` | List<String> | 표시 이름 |
| `buildLinked` | boolean | true면 "빌드 있음"만 표시. 빌드 이름은 어디에도 없다(Q6) |
| `createdAt` | Instant | |
| `mine` | boolean | 수정 버튼 |
| `deletable` | boolean | 삭제 버튼. 작성자 또는 관리자 |

### 자동완성 `ratings/tag-suggest`
| 속성 | 타입 | 설명 |
|---|---|---|
| `tags` | `List<String>` | 후보 태그 이름. 표시명 그대로, 템플릿이 이스케이프 |

- 자동완성 파라미터는 폼 필드 이름 `modTags`의 마지막 쉼표 뒤 토큰이다.

## 3. 화면 문구 (디자이너 확정, 개발자는 그대로)

| 위치 | 문구 |
|---|---|
| 등록 성공(flash) | 평가를 남겼어요. |
| 수정 성공(flash) | 평가를 고쳤어요. |
| 삭제 성공(flash) | 평가를 삭제했어요. |
| 중복 조합 | 이미 이 스위치에 같은 빌드(또는 빌드 없음)로 남긴 평가가 있어요. 수정 화면에서 고쳐 주세요. |
| 충돌 | 그 사이 다른 사람이 먼저 고쳤어요... 새로고침 후 다시 해 주세요. (평가는 작성자만 고치므로 주로 본인의 다른 탭) |
| 빌드 있음 | 빌드 있음 |
| 표본 수 | 평가 N명 · M건 (N = `sampleCount`, M = `ratingCount`. 평균은 M건 기준) |

## 4. 체크

- 이메일 없음(상세·목록·폼·자동완성 모두).
- 빌드 이름은 목록에 없음(`buildLinked`만).
- 삭제 버튼은 `deletable`, 수정 버튼은 `mine`. 템플릿에 `hasRole('ADMIN')` 없음.
- 개발자 테스트에서 상세(요약·목록 포함), 등록·수정 폼, 자동완성 조각이 200으로 렌더링되는지 확인.
