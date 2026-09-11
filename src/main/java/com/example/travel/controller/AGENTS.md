# Development Controller Instructions

## Responsibility

- 이 패키지는 개발 환경 확인용 endpoint만 관리한다.

## Boundaries

- 새 Place, TravelPlan 등 업무 API는 이 패키지에 추가하지 않고 해당 도메인의 `controller` 패키지에 둔다.
- 상태를 저장하거나 비즈니스 로직을 작성하지 않는다.
- 개발 확인용 endpoint의 유지·제거는 `docs/04-api-spec.md`의 `GET /hello` 기준을 따른다.
- 공통 오류 응답 형식과 상태 코드는 `docs/04-api-spec.md`를 따르고 예외를 endpoint별 임의 형식으로 변환하지 않는다.
- 인증 사용자 ID를 request parameter나 body로 받지 않는다.
