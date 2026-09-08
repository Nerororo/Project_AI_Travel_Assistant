# Development Controller Instructions

## Responsibility

- 이 패키지는 개발 환경 확인용 endpoint만 관리한다.

## Boundaries

- 새 Place, TravelPlan 등 업무 API는 이 패키지에 추가하지 않고 해당 도메인의 `controller` 패키지에 둔다.
- 상태를 저장하거나 비즈니스 로직을 작성하지 않는다.
- 개발 확인용 endpoint의 유지·제거는 `docs/04-api-spec.md`의 `GET /hello` 기준을 따른다.
