# Routy Frontend Instructions

## Responsibility

- 이 폴더는 Routy 서비스에 실제로 사용하는 정적 화면과 자체 CSS·JavaScript를 관리한다.

## Boundaries

- 화면 변경 전 해당 기능의 `docs/01-requirements.md`, `docs/04-api-spec.md`를 확인한다.
- 화면은 여행 조건 입력, 장소·호텔 선택, 일정 결과 표시를 지원한다. 결제, 예약, 커뮤니티, 소셜 로그인, 실시간 채팅 UI는 MVP에 추가하지 않는다.
- 국가·도시 입력은 Google 장소 자동완성을 사용할 수 있고, 일정 결과 지도에는 Google Maps JavaScript API로 장소 마커와 경로를 표시한다.
- 상위 static 규칙에 따라 서버 키와 분리한 브라우저용 Google Maps 키만 사용하며, 허용 웹사이트와 실제 사용하는 API에 대한 제한을 모두 적용한다. OpenAI·Google 서버용 API 키 등 서버 비밀키를 HTML·CSS·JavaScript에 포함하지 않는다.
- `css/bootstrap.min.css`와 `lib/`는 외부 템플릿 자산이다. 버전 교체 또는 명시적 요청 없이 직접 수정하지 않는다.
- 화면별 동작은 `js/main.js`, 프로젝트 전용 스타일은 `css/style.css`에 제한한다. 외부 라이브러리 파일을 수정해 기능을 구현하지 않는다.
