# AI Package Instructions

## Responsibility

- 검증된 국가 정보를 바탕으로 도시 후보 이름·국가 코드·추천 이유를 구조화한다.
- 도시 후보 Prompt 생성, OpenAI 호출, 응답 변환은 이 패키지에서 담당한다.
- provider 통신은 `AiClient` 인터페이스 뒤로 분리한다.

## Package Rules

- AI가 Google Place ID, 거리, 최종 추천 순위, 방문 순서를 결정하게 하지 않는다.
- AI가 만든 도시 후보는 `place` 도메인이 Google Places로 실제 도시와 국가 일치를 검증한 뒤에만 사용한다.
- 자유 형식 응답보다 구조화된 응답을 사용하고 DTO validation을 거친다.
- 개발과 자동 테스트에서는 fake client를 사용한다.
- API 키와 사용자·provider 원문을 코드, Git, 로그, 오류 응답에 남기지 않는다.
- 응답 계약 변경은 `docs/04-api-spec.md`를 따른다.
- timeout·재시도·오류 변환은 `docs/09-operations.md`를 따른다.
- 테스트 범위는 `docs/08-test-strategy.md`를 따른다.
