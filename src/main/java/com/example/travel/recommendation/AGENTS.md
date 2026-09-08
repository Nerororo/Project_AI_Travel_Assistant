# Recommendation Domain Instructions

## Responsibility

- Google Places에서 수집된 방문 장소, 호텔, 음식점 후보를 검증하고 추천 점수와 순위를 계산한다.
- 추천 결과와 설명 가능한 추천 근거를 만든다.

## Package Rules

- 추천 규칙과 외부 API 호출 코드를 분리한다.
- 호텔은 이동 거리, 음식점은 식사 시간 충족 여부·음식 선호·동선 이탈 시간과 거리를 고려한다.
- 음식점은 점심·저녁 슬롯에만 추천한다. 긴 체류 장소는 확인된 내부 후보를 우선하고 없으면 인접 후보를 사용한다.
- 포함 관계가 확인되지 않은 음식점을 내부 후보로 표시하지 않는다.
- 경로 계산은 `route` 패키지의 기능을 재사용한다.
- AI가 Google Place ID나 최종 추천 순위를 직접 결정하게 하지 않는다.
- 테스트 범위는 `docs/08-test-strategy.md`의 Recommendation 항목을 따른다.
