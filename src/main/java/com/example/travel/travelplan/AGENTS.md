# Travel Plan Domain Instructions

## Responsibility

- 여행 계획과 날짜별 일정의 생성, 조회, 수정, 삭제를 담당한다.
- 여러 도메인 서비스를 조합하는 흐름을 관리한다.

## Package Rules

- 국가 기반 도시 후보 생성이 필요한 흐름은 `AiService`에 위임한다.
- 국가·도시·장소의 Google Place ID 검증은 `PlaceService`에 위임한다.
- 필수·선택 장소의 교차 중복, 역할 중복, 체류 시간 수정 대상 포함 여부는 저장 전에 검증한다.
- 추천은 `RecommendationService`에 위임한다.
- 거리와 방문 순서 계산은 `RouteService`에 위임한다.
- 하루 시작·종료 시각, 장소별 체류 시간, 식사 슬롯을 조합해 목적지 현지 시각 기준 시간표를 만든다.
- 장소 유형별 기본 체류 시간과 사용자 수정값의 우선순위는 결정적인 정책 클래스로 관리한다.
- `TravelPlanService`에 위 기능의 내부 계산 로직을 중복 구현하지 않는다.
- 저장 흐름은 일부 결과만 남지 않도록 트랜잭션 경계를 명확히 한다.
- Google Places·Routes 호출과 일정 계산을 완료한 뒤 DB 저장 트랜잭션을 시작한다.
- API 계약은 `docs/04-api-spec.md`, 테스트는 `docs/08-test-strategy.md`를 따른다.
