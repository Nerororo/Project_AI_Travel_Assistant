# AI Package Instructions

## Responsibility

- 자연어 요청을 구조화된 선호 DTO로 변환한다.
- Prompt 생성, provider 호출, 응답 변환은 이 패키지에서 담당한다.
- provider 통신은 `AiClient` 인터페이스 뒤로 분리한다.

## Package Rules

- AI가 장소 ID, 거리, 추천 점수, 방문 순서를 결정하게 하지 않는다.
- 자유 형식 응답보다 구조화된 응답을 사용하고 DTO validation을 거친다.
- 개발과 자동 테스트에서는 fake client를 사용한다.
- API 키와 사용자·provider 원문을 코드, Git, 로그, 오류 응답에 남기지 않는다.
- 응답 계약 변경은 `docs/04-api-spec.md`를 따른다.
- timeout·재시도·오류 변환은 `docs/09-operations.md`를 따른다.
- 테스트 범위는 `docs/08-test-strategy.md`를 따른다.
