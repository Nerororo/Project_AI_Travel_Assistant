# Test Harness

## Responsibility

- 자동 테스트와 테스트 전용 fixture를 관리한다.

## Boundaries

- 테스트 책임과 완료 기준은 `docs/08-test-strategy.md`를 따른다.
- 테스트는 실제 OpenAI, 지도 API, 결제 서비스 등 외부 제공자를 호출하지 않는다. fake 또는 mock을 사용한다.
- fixture와 assertion에 API key, 비밀번호, access token, 사용자 자연어 원문을 넣지 않는다.
- 단위 테스트는 알고리즘·도메인 규칙을, 통합 테스트는 Repository·DB 제약을, API 테스트는 validation·상태 코드·오류 응답을 검증한다.
