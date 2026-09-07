# Recommendation Domain Instructions

## Responsibility

- 여행지, 호텔, 음식점 후보의 추천 점수와 순위를 계산한다.
- 추천 결과와 설명 가능한 추천 근거를 만든다.

## Package Rules

- 추천 규칙과 외부 API 호출 코드를 분리한다.
- 호텔은 이동 거리, 음식점은 음식 선호와 동선 이탈 거리를 고려한다.
- 경로 계산은 `route` 패키지의 기능을 재사용한다.
- AI가 장소 ID나 최종 추천 순위를 직접 결정하게 하지 않는다.
- 테스트 범위는 `docs/08-test-strategy.md`의 Recommendation 항목을 따른다.
