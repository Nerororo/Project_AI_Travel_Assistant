# Travel Plan Domain Instructions

## Responsibility

- 여행 계획과 날짜별 일정의 생성, 조회, 수정, 삭제를 담당한다.
- 여러 도메인 서비스를 조합하는 흐름을 관리한다.

## Package Rules

- 선호 분석은 `AiService`에 위임한다.
- 추천은 `RecommendationService`에 위임한다.
- 거리와 방문 순서 계산은 `RouteService`에 위임한다.
- `TravelPlanService`에 위 기능의 내부 계산 로직을 중복 구현하지 않는다.
- 저장 흐름은 일부 결과만 남지 않도록 트랜잭션 경계를 명확히 한다.
- API 계약은 `docs/04-api-spec.md`, 테스트는 `docs/08-test-strategy.md`를 따른다.
