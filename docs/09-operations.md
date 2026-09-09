# 운영 기준

## 1. 목적과 범위

이 문서는 운영 환경을 추가할 때 따라야 할 최소 기준이다. 현재는 설계 문서이며, 장소·지도 공급자는 Google Maps Platform으로 확정했다. 실제 dependency와 설정은 관련 로드맵 작업의 설명과 승인 후 추가한다.

`.env.example` 파일은 제공하지 않는다. 비밀값은 각 개발자·배포 환경이 직접 관리한다.

## 2. 환경별 프로필

| 프로필 | 목적 | DB 스키마 | 외부 AI·지도 API | 로그 |
|---|---|---|---|---|
| `local` | 개인 개발 | Flyway 적용 후 `ddl-auto: validate` | 기본은 fake, 별도 수동 smoke profile/명령에서만 실제 호출 | SQL 상세 로그 허용, 민감 값 금지 |
| `test` | 자동 테스트 | 전용 테스트 DB와 migration | fake/mock만 사용 | 실패 원인에 필요한 최소 로그 |
| `prod` | 배포 환경 | Flyway 적용 후 `ddl-auto: validate` | 실제 호출, timeout·재시도 적용 | 구조화 로그, 민감 값 금지 |

- profile은 `SPRING_PROFILES_ACTIVE`로 선택한다.
- DB URL, 계정, 비밀번호는 환경변수 또는 배포 플랫폼의 secret 관리 기능으로만 전달한다.
- AI 설정 키는 `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_MS`, `OPENAI_MAX_RETRIES`를 사용한다.
- 서버의 Google Places·Routes 호출 키는 `GOOGLE_MAPS_SERVER_API_KEY`로 주입하고 소스·Git·로그에 기록하지 않는다. 키에는 필요한 두 API만 허용한다.
- 브라우저의 Maps JavaScript API 키와 서버 키는 분리한다. 브라우저 키는 허용 웹 origin, 서버 키는 허용 API와 서버 환경에 맞는 제한을 적용한다.
- Google Places 요청은 필요한 필드만 Field Mask로 지정하고 호출량·오류율·비용을 기능별로 관찰한다.
- Place ID 외의 Google Places 콘텐츠는 제공자 정책과 허용 기간을 확인하지 않고 영구 저장하지 않는다. 지도와 장소 정보를 표시할 때 Google attribution 정책을 따른다.

## 3. 로그와 민감 정보

다음 값은 어떤 profile에서도 로그, 예외 메시지, API 응답에 포함하지 않는다.

- DB 비밀번호, API 키, access token, Authorization 헤더
- 사용자 비밀번호와 사용자 자연어 원문
- OpenAI·지도 API의 원본 요청 및 원본 응답

운영 로그에는 요청 식별자, HTTP 상태, 처리 시간, 도메인 오류 코드, 외부 의존성 종류만 기록한다. 사용자 입력 전문과 비밀값은 마스킹 대상이 아니라 기록 금지 대상이다.

## 4. Health check와 의존성 상태

| 대상 | 분류 | 기대 동작 |
|---|---|---|
| 애플리케이션 프로세스 | liveness | 프로세스가 요청을 처리할 수 있는지 확인 |
| MySQL | readiness | 연결·migration 검증이 가능해야 정상 |
| OpenAI | dependency | 장애 시 AI 기능은 `AI_UNAVAILABLE`, 앱·DB health는 유지 |
| Google Places | dependency | 장애 시 국가·도시·장소 검색과 신규 일정 생성만 실패하며 원인을 구분 |
| Google Routes | dependency | 장애 시 경로·시간표가 필요한 신규 생성과 재계산만 `ROUTE_PROVIDER_UNAVAILABLE`로 실패 |

- 외부 API health check는 매 health 요청마다 실제 네트워크를 호출하지 않는다.
- 실제 endpoint는 Spring Boot Actuator 도입을 승인한 뒤 `/actuator/health/liveness`, `/actuator/health/readiness` 형태로 구현한다.
- DB readiness가 실패하면 새 요청을 받을 준비가 되지 않은 상태로 처리한다.

## 5. 외부 API 장애와 비용

- AI의 10초 제한은 재시도와 대기 시간을 포함한 한 번의 Routy 요청 전체 시간이다. 남은 시간 안에서만 네트워크 오류·5xx를 최대 1회 재시도하며, 인증·요청·파싱 오류는 재시도하지 않는다.
- Google Places는 실제 client 구현 전 timeout, 재시도 대상 상태 코드와 rate limit 대응을 명시한다.
- Google Routes는 `TRAFFIC_UNAWARE` route matrix에서 필요한 거리·시간 필드만 요청하고, 실제 client 구현 전 원소별 실패·timeout·rate limit 처리와 호출 크기 제한을 명시한다.
- 외부 호출은 `provider`, `operation`, `outcome`, `latency`, `retryCount`만 집계한다. 원문 payload는 수집하지 않는다.
- 자동 테스트와 기본 `local` 실행은 실제 외부 API를 호출하지 않는다. 실제 키를 별도로 주입한 수동 smoke profile/명령에서만 연결 확인을 허용하며 자동 테스트 및 `./gradlew test`와 분리한다.
- 배포 환경에서는 일/월 호출량과 오류율을 확인할 수 있어야 하며, 공급자 한도 또는 예산 초과 징후는 알림 대상으로 등록한다.

### Client 구현 전 결정 게이트

외부 provider의 실제 제한값·지원 필드·비용 정책은 시간이 지나며 변경될 수 있다. 따라서 추정한 값으로 HTTP client를 구현하거나 dependency를 추가하지 않는다. 각 로드맵의 설계 작업에서 해당 시점의 공식 문서를 확인하고, 아래 항목을 확정해 `04-api-spec.md`와 이 문서에 기록한 뒤에만 구현 작업으로 진행한다. 설계 선택 자체가 바뀌면 `06-decisions.md`에도 이유를 남긴다.

| Provider | 확정 시점 | 구현 전 확정할 항목 |
|---|---|---|
| OpenAI | A1-03 | 사용할 API·모델·structured output 방식, 전체 요청 시간 예산 안의 retry 방식, timeout·provider 오류의 변환, 호출량·비용 관찰 기준 |
| Google Places | P1-06 | 사용할 Places API·Field Mask, 유형·도시 소속 검증에 필요한 필드, 캐시·저장·attribution 정책, timeout·retry 대상·rate limit·호출 크기 |
| Google Routes | R1-06 | route matrix 요청 방식·이동 수단·`TRAFFIC_UNAWARE` 설정, 거리·시간 Field Mask, 요청당 원소 한도, 원소별 실패·timeout·retry·rate limit 처리 |

자동 테스트는 이 결정 이후에도 실제 provider를 호출하지 않으며 fake 또는 mock을 유지한다. 실제 연결 확인은 키를 별도로 주입한 수동 smoke 환경에서만 수행한다.

## 6. 배포와 DB 변경 절차

1. migration 파일을 새 버전으로 추가하고 빈 DB에서 적용을 검증한다.
2. `./gradlew test`를 통과시킨다.
3. 배포 대상 환경의 DB 백업·복구 방법과 호환성 영향을 확인한다.
4. 배포 시 Flyway migration을 먼저 적용하고 애플리케이션을 시작한다.
5. 애플리케이션은 `ddl-auto: validate`로 schema 일치 여부만 검증한다.
6. 파괴적 변경은 이전 애플리케이션 버전과의 호환 기간, 백업, 복구 절차를 별도 변경 계획에 기록한다.

## 7. 배포 전후 체크리스트

### 배포 전

- [ ] `prod` profile에서 비밀값이 코드·Git·로그 설정에 포함되지 않는지 확인
- [ ] migration을 빈 DB와 기존 데이터가 있는 검증 DB에 적용
- [ ] 자동 테스트 통과
- [ ] CORS 허용 origin과 DB 접근 범위 확인
- [ ] AI·지도 API의 timeout, 호출 한도, 비용 알림 기준 확인

### 배포 직후

- [ ] liveness와 readiness 확인
- [ ] `GET /hello` 또는 배포용 health endpoint 확인
- [ ] Place 조회와 TravelPlan 조회의 핵심 흐름 확인
- [ ] migration 버전과 `ddl-auto: validate` 성공 확인
- [ ] 로그에 비밀값·사용자 원문이 없는지 확인
- [ ] AI·지도 API 실패가 애플리케이션/DB 장애와 구분되어 기록되는지 확인
