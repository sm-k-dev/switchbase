# 개발자 질문 (V9 대조, 아직 답하지 못한 항목)

작성: 개발자 / 2026-11-08 / 상태: 질문 원문 (아키텍트 기록과 대조용)

## 원문 (V9 대조 결과 중 1·2번)

1. `review`에 스위치당 한 건을 막는 부분 유니크 인덱스(`uk_review_switch_member`)가 있습니다. 설계 §3 DDL에는 없고, 결정 Q2를 구현한 것입니다.
2. `review.deleted_at`과 `report.deleted_at`이 있습니다. 설계 §3에는 없고, §6-4 정책에 따른 것입니다. `report.deleted_at`은 현재 쓰이지 않습니다.

(원문 끝: 1번과 2번은 설계 문서에 반영할지 정해야 합니다. V9는 이미 push되어 있으므로, 바꿔야 한다면 V10으로 추가합니다.)

## 선택지 (개발자 제안, 결정 전 참고)

- 질문 1 (`uk_review_switch_member`)
  - (가) 유지: Q2 "스위치당 1건"을 DB가 보장합니다. 설계 §3 DDL에 추가 기록합니다.
  - (나) V10에서 제거: 중복 검사는 서비스에만 둡니다. 동시 요청이면 중복이 생길 수 있습니다.
  - 개발자 권장: (가).
- 질문 2 (`review.deleted_at`, `report.deleted_at`)
  - (가) 유지하고 설계 §3에 추가 기록: `review`는 소프트 삭제에 사용 중입니다. `report.deleted_at`은 사용 계획을 정하거나 V10에서 제거합니다.
  - (나) `report.deleted_at`만 V10에서 제거: `review.deleted_at`은 유지합니다.
  - 개발자 권장: `review.deleted_at`은 (가), `report.deleted_at`은 (나).
