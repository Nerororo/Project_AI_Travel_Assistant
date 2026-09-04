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


## AI 응답 계약과 Client 경계

선호 분석 응답은 다음 구조화 JSON만 사용한다.

```json
{
  "interests": ["NATURE", "PHOTOGRAPHY"],
  "crowdPreference": "LOW"
}
```

- `interests`는 `NATURE`, `HISTORY`, `PHOTOGRAPHY`, `SHOPPING`, `ACTIVITY`, `RELAX`, `FOOD`만 포함하며 중복을 허용하지 않는다.
- `crowdPreference`는 `LOW`, `MEDIUM`, `HIGH`, `ANY`만 허용한다.
- JSON Schema 또는 provider structured output을 사용하고, 수신한 값은 Java DTO validation으로 한 번 더 검증한다.
- 계약 검증 실패는 저장·추천에 사용하지 않고 `AI_RESPONSE_INVALID`로 변환한다.
- `AiClient` 인터페이스로 provider 통신을 분리한다. 테스트·로컬 개발에서는 fake 구현체를 사용한다.
- 운영 구현체의 timeout은 최대 10초다. 네트워크 오류 또는 provider 5xx에 한해 최대 1회 재시도하며, 인증·요청·파싱 오류는 재시도하지 않는다.
- 제공자 장애·timeout은 `AI_UNAVAILABLE`으로 변환한다.

## Security

API Key를 코드에 작성하지 않는다.

환경변수 또는 Spring Configuration을 이용한다.

설정 키는 `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_MS`, `OPENAI_MAX_RETRIES`를 사용한다. API 키, 사용자 원문, 제공자 원문 응답은 로그 또는 오류 응답에 남기지 않는다.
