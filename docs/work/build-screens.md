# 빌드 화면 계약 (2단계 빌드 등록)

작성: 아키텍트 / 2026-11-08 / 기준: `docs/work/build-design.md` §5·§6, 디자이너 초안(builds/*)
목적: 개발자(컨트롤러·모델)와 디자이너(템플릿)가 **경로, 템플릿, 모델 속성 이름**을 같게 쓴다. 이름을 바꾸면 양쪽에 같이 알린다.

## 1. 경로와 템플릿

| 화면 | 메서드·경로 | 로그인 | 템플릿 | 비고 |
|---|---|---|---|---|
| 목록 | GET `/builds?page=` | 불필요 | `builds/list` | 숨김 제외, 20건 |
| 상세 | GET `/builds/{id}` | 불필요 | `builds/detail` | 숨김이면 작성자 외 404 |
| 등록 폼 | GET `/builds/new` | 필요 | `builds/form` | |
| 등록 저장 | POST `/builds` | 필요 | (실패 시 `builds/form`) | 성공 → `/builds/{id}` |
| 수정 폼 | GET `/builds/{id}/edit` | 필요 | `builds/form` | 숨김 빌드는 작성자만(그 외 404). 공개 빌드는 누구나(Q1) |
| 수정 저장 | POST `/builds/{id}` | 필요 | (실패·충돌 시 `builds/form`, 충돌은 409) | 성공 → `/builds/{id}` |
| 숨김 | POST `/builds/{id}/hide` | 필요 | — | 성공 → `/builds` (목록, flash "숨겼어요.") |
| 숨김 해제 | POST `/builds/{id}/unhide` | 필요 (작성자만) | — | 성공 → `/builds/{id}` (flash "다시 공개했어요.") |

- 비로그인 쓰기 요청은 로그인 화면으로 보낸다.
- 모든 POST는 `th:action` 사용(CSRF 자동).

## 2. 모델 속성

### 목록 `builds/list`
| 속성 | 타입 | 설명 |
|---|---|---|
| `page` | `Page<BuildView>` | 20건, 최신순 |

### 상세 `builds/detail`
| 속성 | 타입 | 설명 |
|---|---|---|
| `build` | `BuildView` | 상세 대상 (`hidden` 포함) |
| `message` | `String` | flash. 저장·숨김·해제 성공 후 표시 |

- 숨김 안내는 `build.hidden`으로 판단한다. 숨김 빌드는 작성자에게만 도달하므로 템플릿이 작성자 여부를 따로 받지 않는다.
- 수정·숨김 버튼은 로그인 회원에게만 보인다(`sec:authorize="isAuthenticated()"`). 숨김 해제 버튼은 작성자에게만 보인다(숨김 빌드는 작성자 외 404이므로 상세에서 `build.hidden`일 때만 나타난다).

### 폼 `builds/form`
| 속성 | 타입 | 설명 |
|---|---|---|
| `form` | `BuildForm` | 필드: `name`, `housingMaterial`, `plateMaterial`, `mountType`, `keycapMaterial`, `memo`, `version`(long) |
| `buildId` | `Long` | 수정일 때만. 등록이면 null (폼 action 결정) |
| `housingOptions` | `List<Option>` | |
| `plateOptions` | `List<Option>` | |
| `mountOptions` | `List<Option>` | |
| `keycapOptions` | `List<Option>` | |

- 충돌(409)과 검증 실패는 같은 폼을 다시 그린다. 전역 오류는 `BindingResult` 전역 오류로 전달.
- 고정 선택지 select의 기본은 "미지정"(빈 값).

## 3. 뷰 타입

```
BuildView(
  Long id,
  String displayName,        // name이 없으면 "내 키보드"
  String ownerNickname,      // 이메일 금지
  String housingLabel,       // 없으면 null → 화면 "미지정"
  String plateLabel,
  String mountLabel,
  String keycapLabel,
  String memo,
  boolean hidden
)
Option(String code, String label)
```

- 이메일은 어떤 뷰 타입에도 넣지 않는다.
- 표시 이름(label)은 `build-design.md` §3 고정 선택지 표에서 온다.

## 4. 숨김과 권한 (화면 관점)

- 목록: 숨김 빌드를 아예 넣지 않는다.
- 상세: 숨김이면 작성자 외에는 404. 작성자에게는 `build.hidden=true`로 "숨김 상태" 안내와 숨김 해제 버튼을 보인다.
- 숨김 버튼: `onsubmit="return confirm('…')"` 확인 문구. 문구는 디자이너가 정한다.
- 숨김 빌드의 편집 화면(GET·POST)은 작성자만 가능하다. 다른 회원에게는 404(내용 노출 방지, 리뷰 R2 결정).
- 숨김 해제 버튼: 작성자이면서 `build.hidden`일 때만 보인다. 관리자도 보이지 않는다(관리자는 숨김 빌드를 조회할 수 없음).

## 5. 체크

- 이메일 없음(목록·상세·폼 재렌더 모두).
- 개발자 테스트에서 목록·상세·폼(신규·수정)·충돌 재렌더가 200 또는 409로 렌더링되는지 확인한다.
