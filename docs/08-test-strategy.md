# 테스트 전략

## 1. 목표

테스트는 구현 존재 여부가 아니라 여행 계획 결과의 정확성, 외부 장애 시의 예측 가능한 동작, DB 제약의 재현성을 검증한다.

각 기능은 필요한 가장 작은 테스트 수준을 선택한다. 단위 테스트로 충분한 알고리즘에 Spring Context나 실제 DB를 사용하지 않는다.

## 2. 테스트 수준과 책임

| 수준 | 대상 | 검증 내용 | 외부 의존성 |
|---|---|---|---|
| 단위 테스트 | 거리 계산, RouteOptimizer, 추천 점수, 일정 배치 | 입력 대비 결정적 결과와 경계값 | 없음 |
| 서비스 테스트 | Use case Service | 도메인 규칙, 예외 변환, 여러 컴포넌트 조합 | Repository/client fake 또는 mock |
| Repository 통합 테스트 | JPA Repository, migration, 제약조건 | 실제 SQL, 인덱스·UNIQUE·CHECK·FK 제약 | 전용 MySQL 테스트 DB 또는 Testcontainers |
| API 테스트 | Controller와 Global Exception Handler | HTTP 상태, JSON 계약, validation 오류 | MockMvc, Service mock/fake |
| 애플리케이션 통합 테스트 | 핵심 생성 흐름 | DB 저장부터 응답까지의 주요 흐름 | 전용 MySQL 테스트 DB, 외부 client fake |

## 3. 외부 클라이언트 규칙

자동 테스트는 OpenAI나 외부 지도 API를 실제 호출하지 않는다.

```text
Service
  ├── AiClient  → FakeAiClient / mock
  └── MapClient → FakeMapClient / mock
```

- fake 구현체는 고정된 요청에 항상 같은 응답을 반환한다.
- timeout, 5xx, 잘못된 JSON, 빈 검색 결과는 fake 또는 mock으로 재현한다.
- 실제 제공자 연결 확인은 자동 테스트와 분리한 수동 smoke test로만 수행한다. API 키가 없는 환경에서도 전체 테스트가 통과해야 한다.

## 4. Fixture 규칙

- 공통 fixture는 `src/test` 아래 도메인별 factory 또는 builder로 관리한다.
- 위도·경도·거리 기대값·장소 ID는 테스트마다 임의 생성하지 않고 명시적 상수를 사용한다.
- 시간에 의존하는 테스트는 `Clock`을 주입하거나 고정 날짜를 사용한다.
- 테스트 간 DB 상태를 공유하지 않으며, 각 테스트는 독립 실행 가능해야 한다.
- 알고리즘 테스트는 동일 거리 후보가 있을 때 ID 오름차순을 선택하는 등 동률 해소 규칙을 기대값에 포함한다.

## 5. 도메인별 필수 테스트

### Place

- 유효한 장소 등록·조회·수정·삭제
- 이름·타입·좌표 validation 실패 시 `400`
- 존재하지 않는 ID 조회·수정·삭제 시 `404`
- 목록 page, size, sort의 정상·경계·잘못된 값
- 참조 중인 Place 삭제 시 `409`

### TravelPlan

- 시작일이 종료일보다 늦은 요청 거절
- 1일과 14일의 경계값, 15일 요청 거절
- 필수 장소가 누락되지 않는지
- 중복 장소와 일일 6개·전체 용량 초과 거절
- 날짜별 장소 수 차이가 최대 1인지
- 선택 호텔이 추천 후보인지와 관계없이 존재하는지, `HOTEL` 타입인지, 여행 지역과 일치하는지 검증하는지
- 선택된 호텔에서 출발하고 같은 호텔로 돌아오는 날짜별 경로가 계산되는지, 호텔과 음식점 추천 후보가 방문 장소로 저장되지 않는지
- 계획 삭제 시 하위 Aggregate는 삭제되고 Place는 삭제되지 않는지

### Route와 Recommendation

- Haversine 거리 계산의 0 거리·대칭성·대표 좌표 결과
- Nearest Neighbor가 모든 장소를 한 번만 방문하는지
- 동률 후보의 결정적 선택과 빈/단일 장소 입력
- 2-opt 적용 결과가 기준 경로보다 길어지지 않는지
- 호텔 총거리와 음식점 이탈거리 기준의 정렬

### AI

- "도쿄에 가고 싶어"처럼 목적지만 포함한 자연어에서 `destination`이 DTO로 변환되고, 관심사가 없으면 빈 배열과 `ANY`가 반환되는지
- 허용된 `destination`, `interests`, `crowdPreference`가 DTO로 변환되는지
- 알 수 없는 enum, JSON 파싱 실패, 누락 필드가 `AI_RESPONSE_INVALID`로 변환되는지
- timeout과 5xx가 최대 한 번 재시도된 뒤 `AI_UNAVAILABLE`으로 변환되는지
- 인증·요청 오류는 재시도하지 않는지
- 실제 API 키 없이 fake 클라이언트로 테스트가 통과하는지

## 6. 기능 완료 기준

한 기능은 아래를 모두 만족할 때 완료로 표시한다.

1. 핵심 성공 시나리오와 최소 한 개의 실패/경계 시나리오를 자동 테스트한다.
2. API가 있으면 성공·validation·not found 상태 코드를 테스트한다.
3. DB를 변경하면 migration 적용과 제약조건을 통합 테스트한다.
4. 외부 API는 fake 테스트와 수동 smoke test 절차를 분리한다.
5. 전체 `./gradlew test`가 통과한다.

## 7. 실행 구분

| 명령/환경 | 목적 | 실제 외부 API |
|---|---|---|
| `./gradlew test` | 단위·서비스·API·DB 자동 회귀 테스트 | 호출 금지 |
| 로컬 smoke profile | OpenAI·지도 API 연결 및 설정 확인 | 명시적으로 실행할 때만 호출 |
| 배포 전 검증 | migration 및 핵심 사용자 흐름 확인 | 별도 테스트 키·비용 한도 적용 |
