# Travel Plan Domain Instructions

travelplan은 서비스의 핵심 도메인이다.


## Responsibilities

- 여행 계획 생성
- 여행 계획 조회
- 여행 계획 수정
- 여행 계획 삭제
- 날짜별 일정 관리


## Rule

TravelPlanService가 모든 계산을 직접 구현하지 않는다.

다음 기능은 해당 Service에 위임한다.

Preference -> PreferenceService

AI -> AiService

추천 -> RecommendationService

경로 -> RouteService


TravelPlanService는 이 기능들을 조합하여
여행 계획 생성 흐름을 관리한다.