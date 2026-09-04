# AI Package Instructions

ai 패키지는 OpenAI API 통신을 담당한다.

## Responsibilities

- Prompt 생성
- OpenAI API 호출
- 응답 수신
- 구조화된 DTO 변환


## AI 역할

AI는 자연어 이해 및 후보 추천까지만 담당한다.

AI에게 다음 계산을 맡기지 않는다.

- 거리 계산
- 경로 최적화
- 최단 경로 계산
- 호텔 점수 계산
- 맛집 이동거리 계산


## Response Rule

가능하면 자유로운 자연어 응답보다
구조화된 JSON 응답을 사용한다.


## Security

API Key를 코드에 작성하지 않는다.

환경변수 또는 Spring Configuration을 이용한다.