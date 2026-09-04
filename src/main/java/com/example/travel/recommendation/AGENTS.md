# Recommendation Domain Instructions

recommendation 패키지는 추천 점수를 계산한다.


## Responsibilities

- 여행지 추천
- 호텔 추천
- 음식점 추천


## Hotel Recommendation

호텔과 선택된 여행지 사이의
총 이동 거리를 주요 평가 기준으로 한다.


## Restaurant Recommendation

다음을 고려한다.

- 사용자가 먹고 싶은 음식과의 일치
- 여행 경로와의 거리

향후 평점 등을 추가할 수 있다.


## Rule

추천 로직과 외부 API 호출 로직을
가능한 한 분리한다.