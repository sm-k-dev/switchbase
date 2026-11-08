# 코드 리뷰: member (회원가입·로그인)

작성: 리뷰어 / 2026-11-08

## 판정: 통과

- 1차 정식 리뷰에서 반려 4건(layout nickname이 AdminAccessTest를 깨뜨림 1, MockMvc `redirectedUrlPattern` 매처 3)을 냈고, 모두 수정 확인.
- 테스트: 9개 스위트 합계 42개, 실패 0·오류 0·건너뜀 0 (`build/test-results/test/*.xml`, 개발자가 `sg docker -c "./gradlew test"`로 실행). 리포트 이후 `src/` 변경 없음.
- 보안: 미인증 차단은 비밀번호 확인 뒤, 토큰은 해시 저장·24시간·1회용, CSRF 유지, Referrer-Policy no-referrer, 운영 로그 노출은 `@Profile("!prod")`로 차단(prod 기동 실패는 사용자가 의도로 확인).
- 남은 권고(반려 아님): `GET /verify`의 상태 변경, 토큰 동시 소비 경합, 만료 토큰 정리, V4 참고 사항 — `docs/work/member-review.md` 참고.
- 상세: `docs/work/member-review.md` (사전 리뷰 + 정식 리뷰 섹션)
