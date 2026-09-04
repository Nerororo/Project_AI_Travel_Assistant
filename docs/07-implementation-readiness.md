# 구현 준비도와 작업 기준

## 1. 현재 기준선 (2026-09-04)

| 항목 | 상태 | 근거 / 다음 조치 |
|---|---|---|
| Spring Boot 웹 애플리케이션 | 준비됨 | `GET /hello`와 Web MVC 테스트 의존성이 있다. |
| Java 21 / Gradle | 준비됨 | Java toolchain과 Gradle wrapper가 있다. |
| MySQL 컨테이너 | 준비됨 | `docker-compose.yaml`에 MySQL 8.4 서비스가 있다. |
| Datasource/JPA 설정 파일 | 초안 | `application.yml`에 설정은 있으나 현재 `build.gradle`에 JPA와 MySQL 드라이버 의존성이 없다. |
| 장소·여행계획 도메인 | 설계됨 | 엔티티와 API는 문서에 정의되어 있으나 구현은 시작 전이다. |
| OpenAI 연동 | 설계 전 | API 키 이름, 호출 방식, 모델, 시간 제한, 실패 시 동작을 확정해야 한다. |
| 인증 | 후순위 | 핵심 MVP 완료 뒤 Spring Security와 JWT를 도입한다. |

문서에서 말하는 “완료”는 코드가 존재하는 상태가 아니라, 테스트를 통과하고 로컬에서 재현 가능한 상태를 뜻한다.

## 2. MVP를 만들 수 있는 정도

현재 문서는 서비스 목표, 도메인 경계, 핵심 알고리즘의 책임 분리, 테이블 초안, API 흐름, 단계별 개발 순서를 갖추고 있다. 따라서 Codex가 작은 작업 단위로 Spring 백엔드를 구현하기에는 좋은 출발점이다.

다만 아래 항목이 확정되기 전에는 통합 여행계획 API를 바로 만들지 않는다.

1. 장소 데이터의 출처와 초기 적재 방식
2. AI 응답 JSON 스키마와 실패·재시도 정책
3. 일정 생성의 입력 제약과 배치 규칙
4. API의 상태 코드, 오류 코드, 페이징 계약

## 3. 구현 시작 전 결정

### 3.1 장소 데이터

MVP는 외부 장소 API를 실시간으로 호출하지 않는다. 검증 가능한 시드 데이터로 시작한다.

- 지역은 첫 릴리스에서 `부산` 하나로 제한한다.
- 관광지, 호텔, 음식점을 합쳐 최소 20건을 준비하고, 모든 레코드에 유효한 위도·경도를 넣는다.
- 시드 데이터의 출처, 수집일, 라이선스/이용 조건은 `docs/data-sources.md` 또는 README에 기록한다.
- 데이터가 없는 조건에는 추천을 억지로 만들지 않고 `PLACE_CANDIDATE_NOT_FOUND`를 반환한다.

### 3.2 AI 계약

AI는 자연어를 아래의 제한된 값으로 변환하는 역할만 맡는다. 장소 ID, 거리, 방문 순서는 AI가 결정하지 않는다.

```json
{
  "interests": ["NATURE", "PHOTOGRAPHY"],
  "crowdPreference": "LOW"
}
```

- `interests`: `NATURE`, `HISTORY`, `PHOTOGRAPHY`, `SHOPPING`, `ACTIVITY`, `RELAX`, `FOOD`만 허용한다.
- `crowdPreference`: `LOW`, `MEDIUM`, `HIGH`, `ANY`만 허용한다.
- 응답은 JSON Schema 또는 provider의 structured output으로 제한하고, 서버 DTO 검증을 한 번 더 수행한다.
- 모델명, API 키 환경 변수명(예: `OPENAI_API_KEY`), timeout, 최대 재시도 횟수는 설정으로 분리한다.
- 타임아웃·파싱 실패·제공자 오류는 각각 기록하고 클라이언트에는 `AI_UNAVAILABLE` 또는 `AI_RESPONSE_INVALID`를 반환한다. 실패 시 임의의 선호를 만들어 저장하지 않는다.
- 개발과 테스트에서는 `AiClient` 인터페이스의 fake 구현을 사용해 네트워크와 비용 없이 재현한다.

### 3.3 일정 생성 규칙

- 여행일 수는 `endDate - startDate + 1`이며 시작일이 종료일보다 늦으면 `400`이다.
- 필수 장소는 중복 없이 모두 포함한다. 존재하지 않는 ID는 `PLACE_NOT_FOUND`이다.
- MVP는 영업시간과 실제 도로 이동시간을 고려하지 않는다는 사실을 응답의 `assumptions`에 명시한다.
- 장소 수가 여행일 수보다 적어도 빈 날짜를 만들지 않는다. 장소 수가 너무 많아 당일 배치 기준을 넘으면 `PLAN_CAPACITY_EXCEEDED`를 반환하거나 사용자가 후보를 줄이도록 안내한다. 이때의 일일 최대 방문 수는 구현 전에 상수와 문서로 확정한다.
- 거리 단위는 km, 소수 둘째 자리 반올림으로 통일하고 Haversine 공식을 사용한다.

## 4. API 공통 계약

`04-api-spec.md`의 모든 엔드포인트에 아래 기준을 적용한다.

| 상황 | 응답 |
|---|---|
| 생성 성공 | `201 Created`와 생성 리소스 또는 ID |
| 조회/수정 성공 | `200 OK` |
| 삭제 성공 | `204 No Content` |
| 형식·필수값 오류 | `400 Bad Request` |
| 존재하지 않는 리소스 | `404 Not Found` |
| AI 제공자 장애 | `503 Service Unavailable` |

오류 본문은 다음 형식을 사용한다. `traceId`는 로그 상관관계가 도입될 때 추가한다.

```json
{
  "code": "PLACE_NOT_FOUND",
  "message": "장소를 찾을 수 없습니다.",
  "fieldErrors": []
}
```

`GET /api/places`에는 데이터가 늘어날 때를 대비해 처음부터 `page`, `size`, `sort`를 정의한다. 기본값과 최대 `size`는 구현 시 명시하고, 목록 응답에는 `content`, `page`, `size`, `totalElements`를 포함한다.

## 5. Codex 작업 단위의 완료 조건

각 작업은 한 도메인 또는 한 API 흐름으로 제한하고 아래 조건을 충족할 때 완료한다.

1. 관련 `AGENTS.md`, 요구사항, DB/API 문서를 확인한다.
2. Entity, DTO, Service, Controller의 책임을 분리한다.
3. 요청 DTO 검증과 공통 오류 응답을 추가한다.
4. 핵심 서비스 또는 알고리즘의 단위 테스트를 작성한다.
5. `./gradlew test`를 실행한다.
6. API·스키마·결정이 달라지면 관련 문서와 ADR을 같은 변경에 반영한다.

권장 첫 구현 단위는 다음과 같다.

```text
JPA/MySQL 의존성 및 연결 검증
→ Place CRUD + 시드 데이터 + 통합 테스트
→ 거리 계산 + 단위 테스트
→ 경로 최적화 + 단위 테스트
→ TravelPlan 기본 CRUD
→ AI preference 분석(fallback/fake 포함)
→ 추천과 최종 일정 조합
```

## 6. 배포 전 최소 검증

- `.env`는 추적되지 않고, `.env.example`에는 키 이름만 제공한다.
- 운영 환경에서 `ddl-auto: update`, SQL 상세 로그를 사용하지 않는다.
- 헬스 체크는 DB 연결 상태를 확인한다.
- OpenAI 키와 DB 비밀번호는 로그·오류 응답·테스트 fixture에 포함하지 않는다.
- Docker Compose를 포함한 새 환경에서 README만 보고 실행할 수 있어야 한다.
