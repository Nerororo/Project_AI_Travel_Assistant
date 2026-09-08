# Place Domain Instructions

## Responsibility

- Google Places에서 국가, 도시, 관광지, 호텔, 음식점을 조회하고 유형을 검증한다.
- 사용자가 선택한 Google Place ID를 내부 `Place` 참조로 저장하고 중복을 방지한다.
- provider 통신은 `GooglePlacesClient` 인터페이스 뒤로 분리한다.
- TravelPlan 저장 전 도시·방문 장소·호텔의 존재, 허용 유형, 선택 도시 소속을 검증한다.

## Package Rules

- 여행 일정 생성이나 경로 최적화 로직을 구현하지 않는다.
- AI API를 직접 호출하지 않는다.
- Google 장소 본문과 검색 후보 전체를 별도 허용 근거 없이 영구 저장하지 않는다.
- Google 응답으로 도시 소속을 확인할 수 없으면 일치한다고 추측하지 않는다.
- Controller는 Entity를 직접 반환하지 않고 DTO를 사용한다.
- 스키마 변경은 `docs/03-database.md`를 따른다.
- HTTP 계약 변경은 `docs/04-api-spec.md`를 따른다.
- 테스트 범위는 `docs/08-test-strategy.md`의 Place 항목을 따른다.
