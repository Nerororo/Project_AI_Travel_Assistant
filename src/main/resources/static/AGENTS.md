# Static Asset Instructions

## Responsibility

- 브라우저에 제공되는 HTML, CSS, JavaScript, 이미지 자산을 관리한다.

## Boundaries

- 정적 화면에는 OpenAI·Google 서버용 API 키 등 서버 비밀키, access token, DB 정보, 사용자 원문을 포함하지 않는다.
- 브라우저에서 사용하는 Google Maps 키는 서버 키와 분리하고, 허용 웹사이트와 실제 사용하는 API에 대한 제한을 모두 적용한 경우에만 사용할 수 있다. 이 예외는 서버 비밀키나 제한 없는 키에는 적용하지 않는다. 설정 파일에 실제 키를 기록하지 않는 상위 resources 규칙은 유지한다.
- 백엔드 비즈니스 규칙, 거리 계산, 경로 최적화, 추천 점수 계산을 JavaScript로 복제하지 않는다.
- 화면의 API 요청·응답 형식 변경 전 `docs/04-api-spec.md`를 확인한다.
- 실제 서비스 화면과 템플릿 원본의 역할은 각 하위 폴더의 `AGENTS.md`를 따른다.
