# Route Domain Instructions

## Responsibility

- 장소 사이 거리와 거리·이동 시간 행렬을 계산한다.
- 방문 순서와 총 이동 거리·시간을 계산한다.

## Package Rules

- 경로 계산에 AI API를 사용하지 않는다.
- 최초 구현은 Nearest Neighbor를 사용한다.
- 알고리즘은 Spring과 DB에 의존하지 않는 순수 Java 로직으로 작성한다.
- Google Routes 통신은 `route/client`에만 두고, `route/algorithm`에는 HTTP Client나 provider DTO를 전달하지 않는다.
- 최종 시간표는 실시간 교통이 아닌 정적 이동 시간을 사용한다.
- 동률 처리 규칙을 결정적으로 유지한다.
- 알고리즘을 바꾸면 시간 복잡도를 설명하고 전후 결과를 테스트한다.
- 테스트 범위는 `docs/08-test-strategy.md`의 Route 항목을 따른다.
