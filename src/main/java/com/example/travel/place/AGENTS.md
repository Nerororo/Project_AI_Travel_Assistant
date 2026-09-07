# Place Domain Instructions

## Responsibility

- 관광지, 호텔, 음식점을 `Place`와 `PlaceType`으로 관리한다.
- 장소 등록, 조회, 검색, 저장을 담당한다.

## Package Rules

- 여행 일정 생성이나 경로 최적화 로직을 구현하지 않는다.
- AI API를 직접 호출하지 않는다.
- Controller는 Entity를 직접 반환하지 않고 DTO를 사용한다.
- 스키마 변경은 `docs/03-database.md`를 따른다.
- HTTP 계약 변경은 `docs/04-api-spec.md`를 따른다.
- 테스트 범위는 `docs/08-test-strategy.md`의 Place 항목을 따른다.
