# SwitchBase

Mechanical Keyboard Switches, structured by data and taste.

> 모든 결정과 판단은 사람이 하고, AI는 활용할 뿐이다.

## 실행

Docker Desktop을 켜고 `./gradlew bootRun`

## 첫 관리자

실행 전에 환경 변수 `ADMIN_PASSWORD`를 설정하면 기동 때 관리자 계정(`ADMIN_EMAIL`, 기본 `admin@switchbase.local`)이 만들어집니다. 설정하지 않으면 관리자 생성을 건너뜁니다.

## 재가입 차단 키

탈퇴 회원의 이메일 해시(재가입 차단)는 서버 비밀값(HMAC 키)으로 만듭니다. 기동 전에 환경 변수 `SIGNUP_BLOCK_SECRET`을 설정하세요. 값이 없으면 기동하지 않습니다. 테스트는 `build.gradle.kts`에서 테스트 전용 값을 줍니다. 운영 값은 저장소에 두지 마세요.
