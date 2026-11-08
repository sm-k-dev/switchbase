# 유저 위키 화면 계약 (2단계 위키, 1단계 구현 기준)

작성: 아키텍트 / 2026-11-08 / 기준: `docs/work/wiki-design.md` §4·§5·§7
목적: 개발자(컨트롤러·모델)와 디자이너(템플릿)가 **경로, 템플릿 이름, 모델 속성 이름**을 같게 쓰도록 맞춘다. 여기 적힌 이름을 바꾸려면 양쪽에 같이 알린다.

## 1. 경로와 템플릿

| 화면 | 메서드·경로 | 로그인 | 템플릿 | 설명 |
|---|---|---|---|---|
| 수정 폼 | GET `/switches/{id}/edit` | 필요 | `switches/edit` | 기존 `admin/switch-form.html`을 공용으로 옮긴 화면. 안내 "수정하면 바로 공개됩니다." |
| 수정 저장 | POST `/switches/{id}` | 필요 | (실패 시 `switches/edit`) | 성공 → `/switches/{id}` 리다이렉트 + flash `message` |
| 이력 | GET `/switches/{id}/history` | 불필요 | `switches/history` | 리비전 목록. 두 개 고르면 비교로 이동 |
| 비교 | GET `/switches/{id}/compare?from=N&to=M` | 불필요 | `switches/compare` | 스펙 필드 표, 바뀐 행 강조 |
| 되돌리기 확인 | GET `/switches/{id}/revisions/{no}/revert` | 필요 | `switches/revert` | 되돌릴 내용(#N-1 → #N 차이) 표시. 권한·최신 아님이면 버튼 대신 사유 |
| 되돌리기 실행 | POST `/switches/{id}/revisions/{no}/revert` | 필요 | (실패 시 `switches/revert`) | 성공 → `/switches/{id}` + flash `message` "#N 수정을 취소했어요" |
| 최근 변경 | GET `/changes?page=` | 불필요 | `changes/list` | 전체 리비전 최신순 20건 |
| 상세(기존) | GET `/switches/{id}` | 불필요 | `switches/detail` | 기존 화면. "수정 이력" → `/switches/{id}/history` 링크 추가 |

- 등록(`/admin/switches/new`)은 기존 그대로 관리자 전용. 수정 폼만 `/switches/{id}/edit`로 옮긴다. 기존 `/admin/switches/{id}/edit`과 POST `/admin/switches/{id}`는 제거한다(개발자).

## 2. 모델 속성 이름

| 템플릿 | 속성 | 타입 | 비고 |
|---|---|---|---|
| `switches/edit` | `form` | `SwitchForm` | 수정 폼. `version` 포함 |
| `switches/edit` | `switchId` | `Long` | 폼 action에 사용 |
| `switches/edit` | `conflict` | `boolean`(선택) | 낙관적 잠금 충돌 시 true. 문구는 `BindingResult` 전역 오류로도 전달 |
| `switches/history` | `sw` | `KeyboardSwitch` | 제목용 |
| `switches/history` | `revisions` | `List<SwitchRevision>` | 최신순 |
| `switches/history` | `latestNo` | `int` | 되돌리기 버튼 표시 판단용 |
| `switches/compare` | `sw` | `KeyboardSwitch` | |
| `switches/compare` | `from`, `to` | `SwitchRevision` | 두 리비전 |
| `switches/compare` | `rows` | `List<CompareRow>` | `CompareRow(String label, String fromValue, String toValue, boolean changed)` |
| `switches/compare` | `descriptionFrom`, `descriptionTo` | `String` | 설명은 전후를 나란히 표시(줄 diff 없음) |
| `switches/revert` | `sw`, `target`, `previous` | `KeyboardSwitch`, `SwitchRevision`, `SwitchRevision` | target = 되돌릴 리비전 #N, previous = #N-1 |
| `switches/revert` | `rows` | `List<CompareRow>` | previous → 현재 스펙 차이 (되돌릴 내용) |
| `switches/revert` | `canRevert` | `boolean` | false면 버튼 숨김 |
| `switches/revert` | `denyReason` | `String` | canRevert=false일 때의 사유 문구 |
| `switches/revert` | `form` | `RevertForm` | `version`(long), `editSummary`(선택) |
| `changes/list` | `page` | `Page<SwitchRevision>` | 20건. 항목: 스위치 이름(상세 링크), #번호, 시각, 편집자 표시(`editorDisplay`), 요약 |
| 공통 | `message` | `String` | flash. 저장·취소 성공 후 상세에서 표시 |

- 편집자 표시는 모든 화면에서 `r.editorDisplay`만 쓴다. 이메일은 어떤 화면에도 나오지 않는다(0단계 규칙).
- 폼 필드 이름은 기존 `SwitchForm`과 같다(`manufacturer`, `brand`, `modelName`, `variant`, `switchType`, `actuationForceG` …, `aliases`, `editSummary`, `sourceUrl`, `version`). 되돌리기 폼은 `version`, `editSummary`만 쓴다.

## 3. 화면 문구 (디자이너가 최종 확정, 개발자는 그대로 쓴다)

| 위치 | 문구 |
|---|---|
| 수정 안내 | 수정하면 바로 공개됩니다. |
| 수정 저장 성공(flash) | 수정했어요. |
| 충돌 | 그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요. |
| 되돌리기 성공(flash) | #N 수정을 취소했어요. |
| 되돌리기 버튼 | 이 수정 취소 |
| 되돌리기 불가: 권한 없음 | 작성자 본인이나 관리자만 취소할 수 있어요. |
| 되돌리기 불가: 최신 아님 | 뒤에 다른 수정이 있어서 취소할 수 없어요. |
| 되돌리기 불가: 첫 등록 | 처음 등록은 취소할 수 없어요. |
| 취소 요약 자동값 | #N 수정 취소 |
| 별칭·출처 안내 | 별칭과 출처는 되돌리기에 포함되지 않아요. |
| 비교 설명 | 설명은 전후를 나란히 보여 줘요. |

## 4. 디자이너 체크

- 공용 폼은 `switches/edit`. 신규 등록 화면(`admin/switch-form`)과 같은 조각을 쓰되 제목·action만 다르게.
- `changes/list`, `switches/history`는 htmx를 쓰지 않는다(일반 링크·폼).
- 되돌리기 확인 화면의 버튼은 POST 폼이고 `th:action`을 쓴다(CSRF 자동).
- 이메일 표시 금지. 편집자는 `r.editorDisplay`.
