# 운영 기준

## 1. 목적과 범위

이 문서는 Routy의 환경 설정, 비밀값, 외부 API, 호출 한도, 로그, 장애 대응과 배포 게이트를 정의한다. 현재는 목표 운영 설계이며 실제 설정과 dependency는 해당 구현 작업의 Change Envelope에서 추가한다.

외부 제공자는 다음과 같이 구분한다.

| 기능 | 제공자 |
|---|---|
| 지역·메뉴 자연어 분석 | OpenAI |
| 관광지·숙소·음식점 검색 | 카카오 Local |
| 자동차 경로 | 카카오모빌리티 |
| 대중교통 경로 | 카카오맵 |
| 행정구역 대표 좌표 | 출처가 명확한 공공 행정구역 데이터 |

카카오 좌표의 작성 중 일시 사용과 즉시 폐기 방식은 2026-09-11 카카오 DevTalk 답변으로 허용 범위를 확인했다. 운영 배포 전에는 이 수명 규칙이 실제 구현과 테스트에서 지켜지는지 검증한다.

## 2. 환경별 프로필

| 프로필 | DB | 외부 API | 로그 |
|---|---|---|---|
| `local` | Flyway 후 `ddl-auto: validate` | 기본 Fake, 별도 smoke에서만 실제 호출 | 개발 진단용, 금지 데이터 제외 |
| `test` | 격리된 MySQL과 migration | Fake/mock만 | 실패 원인에 필요한 최소 정보 |
| `prod` | Flyway 후 `ddl-auto: validate` | 실제 Client와 운영 한도 | 구조화 로그, 금지 데이터 제외 |
| `smoke` | 비영속 또는 전용 검증 환경 | 명시적으로 선택한 실제 제공자만 | 원문과 좌표 기록 금지 |

- profile은 `SPRING_PROFILES_ACTIVE`로 선택한다.
- 기본 local 실행과 `./gradlew test`는 실제 외부 API를 호출하지 않는다.
- smoke profile은 운영 배포와 분리하고 제한된 키와 작은 호출 예산을 사용한다.
- `.env.example`에 실제 값이나 실제 값처럼 보이는 샘플을 넣지 않는다.
- `test`의 DB 연결 정보는 Testcontainers와 Spring Boot service connection이 주입한다. 개발자 로컬 DB 환경 변수에 의존하거나 H2로 대체하지 않는다.
- 로컬 Compose의 `travel-mysql`은 개발용 영속 DB이며 자동 테스트가 재사용하지 않는다. 통합 테스트는 데이터 격리와 재현성을 위해 매 실행마다 Testcontainers가 관리하는 별도 MySQL을 사용한다.
- Windows Java 프로세스가 Docker Desktop named pipe 접근을 거부당하는 이 개발 환경에서는 저장소 설정이나 테스트 DB 대상을 바꾸지 않고 루트 `test.ps1`로 전체 테스트를 실행한다. 스크립트는 WSL의 `/var/run/docker.sock`, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, Mockito attach에 필요한 컨테이너 한정 권한·JVM 옵션과 임시 프로젝트 캐시를 일관되게 적용한다. Docker Desktop의 WSL integration이 켜져 있어야 하며 첫 실행에는 고정된 Gradle/JDK 이미지 다운로드가 필요할 수 있다.

## 3. 환경 변수와 비밀값

환경 변수 이름은 구현 단계에서 설정 클래스와 함께 최종 확정한다. 최소 범주는 다음과 같다.

| 범주 | 예시 이름 | 원칙 |
|---|---|---|
| DB | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 환경 또는 secret manager에서 주입 |
| JWT | `JWT_ACTIVE_KEY_ID`, key ID별 JWT secret, `JWT_ACCESS_TOKEN_TTL` | 최소 256-bit 무작위 secret, access token 1시간, 코드·Git 저장 금지 |
| OpenAI | `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_MS` | 서버 전용 |
| 카카오 장소 | `KAKAO_LOCAL_API_KEY` | 서버 전용, 필요한 API만 허용 |
| 카카오 자동차 | `KAKAO_MOBILITY_API_KEY` | 서버 전용, 필요한 API만 허용 |
| 카카오 대중교통 | `KAKAO_REST_API_KEY` | 카카오맵 REST 대중교통 경로 전용, 앱·API 제한 확인 |
| 토큰 서명 | `PLACE_SELECTION_TOKEN_SECRET` | JWT secret과 분리 |

- 키·비밀번호·서명 secret은 소스, Git, 이미지, fixture와 문서 예시에 저장하지 않는다.
- 클라이언트에 노출되는 키가 필요하면 서버 키와 분리하고 허용 origin·도메인·API 범위를 최소화한다.
- 키를 교체할 수 있도록 설정과 Client 생성 코드를 분리한다.
- JWT key 교체 시 새 active key로만 발급하고 이전 key는 1시간 동안 검증한 뒤 제거한다. token header의 알고리즘과 key ID는 서버 allowlist에 있는 값만 허용한다.
- 운영 키가 노출되면 즉시 폐기·재발급하고 접근 로그와 호출량을 점검한다.

## 4. 로그와 관측 정보

다음 값은 모든 profile의 로그, trace, 오류 응답과 metric label에 기록하지 않는다.

- 비밀번호, API 키, JWT, Authorization·Cookie 헤더
- 사용자 자연어 원문, 이메일 전체와 개인정보
- OpenAI 요청·응답 원문
- 카카오 요청·응답 원문
- 카카오 장소 좌표·주소·전화번호·카테고리·제공자 장소명
- 경로 원문, 경로 좌표와 `selectionToken` 원문
- DB 연결 문자열의 자격 증명

이 값들은 마스킹해서 남기는 대상이 아니라 기록 금지 대상이다.

운영 로그는 다음과 같은 비식별 정보만 사용한다.

- 서버가 생성한 request ID
- 인증 사용자 내부 ID가 필요하면 복구 불가능한 운영용 표현
- endpoint 또는 use case
- HTTP 상태와 도메인 오류 코드
- 처리 시간
- 외부 제공자 종류, operation, outcome, latency, retry count
- rate limit 차단 종류와 남은 대기 시간

metric label에는 사용자 ID, 장소 ID, 검색어처럼 cardinality가 큰 값을 넣지 않는다.

## 5. 호출 한도와 비용 차단

초기 사용자별 한도는 다음과 같다.

| 기능 | 분당 | 일일 |
|---|---:|---:|
| 장소 검색 | 20 | 300 |
| 자동차 경로 | 60 | 120 |
| AI 지역 추천 | 2 | 10 |
| AI 메뉴 분석 | 3 | 15 |
| 대중교통 경로 | 60 | 120 |

- 일일 한도는 `Asia/Seoul` 자정에 초기화한다.
- 재시도와 fallback 전 실제 외부 호출은 각각 사용량에 포함한다.
- 여러 인접 구간을 제공자 요청 하나로 묶을 수 있다면 제공자의 과금·쿼터 단위를 확인해 계수한다.
- 일반 사용자 한도 초과는 429와 `retryAfterSeconds`를 반환한다. 완료 일정의 경로 쿼터 부족은 전체 구간 추정 fallback과 warning으로 처리한다.
- 한도 숫자는 정책 클래스와 설정에 한 번만 정의하고 코드 곳곳에 복제하지 않는다.
- 각 지도 API의 공식 일일 무료 한도를 따로 집계하고 90%에 도달하면 해당 API의 새로운 외부 호출을 차단한다.
- 카카오맵 대중교통 경로는 공식 일일 1,000건을 기준으로 900건에서 신규 호출을 차단한다.
- 공식 무료 쿼터 제공 조건은 카카오 디벨로퍼스에서 해당 기능을 처음 활성화한 앱인지 운영 전에 확인한다.
- 유료 초과 사용을 자동 승인하지 않는다.
- 서비스 전체 AI 예산은 실제 모델·요금을 확인한 뒤 확정한다.

완료 생성은 호출 전에 필요한 실제 제공자 요청 수를 계산하고 사용자·서비스 쿼터를 원자적으로 확보한다. 확보에 실패하면 일부 구간만 호출하지 않고 외부 경로 API 호출을 0건으로 유지한 채 전체 구간을 Haversine 기반 예상시간으로 계산한다. 이 fallback은 응답 warning과 metric에 남긴다.

최소 metric은 기능별 요청 수, 외부 호출 수, 성공률, 오류율, latency, 재시도, fallback, 사용자 한도 차단과 서비스 전체 차단이다. 비용은 제공자 콘솔 수치와 정기적으로 대조한다.

fallback 원인은 일정·좌표·경로 payload와 연결하지 않은 집계 metric으로만 기록한다. 생성 응답의 warning과 구간별 이동시간 출처는 DB에 저장하지 않는다.

## 6. requestId 중복 처리

외부 비용이 드는 POST 요청은 클라이언트가 생성한 UUID 형식 `requestId`를 사용한다.

- 사용자·기능·requestId 조합과 처리 상태만 MySQL `request_executions`에 10분간 보관한다.
- 결과 리소스 ID, 요청 payload, 응답 payload, 좌표와 사용자 원문은 보관하지 않는다.
- 동일 요청이 처리 중이면 409 `REQUEST_IN_PROGRESS`를 반환한다.
- 동일 요청이 이미 성공했으면 409 `REQUEST_ALREADY_COMPLETED`를 반환한다.
- 두 중복 상태에서는 외부 호출·저장 로직과 호출량 차감을 반복하지 않는다. 10분 수명이 지난 requestId는 새 요청으로 처리한다.
- 사용자별 분·일 카운터와 API별 서비스 전체 일 카운터는 MySQL `api_usage_counters`에서 모든 서버 인스턴스가 공유한다.
- 카운터는 조건부 UPDATE와 짧은 트랜잭션으로 확보하고 커밋한 뒤 외부 API를 호출한다. 호출 직전 장애로 보수적으로 소비된 수량은 복구하지 않는다.
- 분·일 기간이 끝난 카운터와 만료 requestId 행은 주기 작업으로 묶어서 삭제하며 판정 시 `expires_at`을 항상 확인한다.

## 7. timeout, 재시도와 오류 변환

| 상황 | 재시도 | 결과 |
|---|---:|---|
| 인증·권한·잘못된 요청 | 없음 | 제공자 설정 또는 요청 오류 |
| 정상 빈 장소 검색 | 없음 | 200과 빈 결과 |
| 정상 경로 없음 | 없음 | 422 `ROUTE_NOT_FOUND` |
| timeout·연결 실패·일시적 5xx | 최대 1회 | 아래 기능별 처리 |
| 잘못된 구조의 AI 응답 | 최대 1회 | 재실패 시 `AI_RESPONSE_INVALID` |
| 일반 기능의 사용자 한도 초과 | 없음 | 429와 retryAfterSeconds |
| 완료 생성의 경로 쿼터 부족 | 전체 구간 Haversine 예상시간 | 성공 응답 warning, 외부 경로 호출 0건 |

경로의 기술적 장애가 한 번 재시도 뒤에도 계속되면 Haversine 추정 시간을 10분 단위로 올려 일정 생성을 계속할 수 있고 응답에 warning을 포함한다. 정상 경로 없음에는 fallback을 적용하지 않는다.

장소 검색과 AI는 오류를 임의 데이터로 대체하지 않는다. timeout과 전체 요청 시간 예산은 실제 Client 구현 전 제공자별 공식 문서와 사용자 경험을 기준으로 확정한다.

## 8. Health와 의존성 상태

| 대상 | 분류 | 기대 동작 |
|---|---|---|
| 애플리케이션 | liveness | 프로세스가 요청을 처리할 수 있음 |
| MySQL | readiness | 연결과 schema 검증 가능 |
| 정적 지역 데이터 | readiness | 시작 시 검증과 적재 성공 |
| OpenAI | dependency | AI 기능만 실패, 기본 앱 health 유지 |
| 카카오 장소 | dependency | 신규 장소 검색만 실패 |
| 자동차·대중교통 경로 | dependency | 신규 완료 검증에 영향, 저장 조회는 유지 |

- health 요청마다 실제 외부 API를 호출하지 않는다.
- 최근 호출 결과와 내부 상태는 원문 없이 별도 metric으로 관찰한다.
- Actuator가 필요하면 dependency 추가 이유와 endpoint 노출 범위를 먼저 설명한다.
- 완료·공유 일정 조회는 모든 외부 제공자가 장애여도 동작해야 한다.

## 9. 데이터 수명과 정책 준수

### 영속 저장 허용

- 카카오 장소 ID와 외부 상세 URL
- 사용자 작성 장소 표시 이름과 메모
- 사용자가 확정한 체류 시간
- 저장된 방문 순서와 10분 단위 이동 시간
- 지역 ID와 표시 이름 snapshot

### 영속 저장 금지

- 카카오 장소명, 좌표, 주소, 전화번호와 카테고리
- 검색 요청·응답 원문과 검색 결과 캐시
- 실제 경로 원문, 경로 좌표와 지도 타일
- AI 사용자 원문과 응답 원문

완료·공유 화면은 저장 데이터와 카카오 외부 링크만 사용한다. 카카오 API를 재호출하거나 지도·경로선을 표시하지 않는다.

### 확인된 카카오 데이터 수명 계약

2026-09-11 카카오 DevTalk에서 "일시적으로 저장 및 참조 후 즉시 폐기하는 구조는 허용 가능합니다."라는 답변을 받았다. 장소 선택 토큰, 좌표 기반 estimate와 일정 생성 흐름은 다음 조건을 지켜야 한다.

1. 검색 결과 좌표를 브라우저 JavaScript 메모리에만 보관한다.
2. 브라우저는 estimate와 create에 필요한 값을 각 요청으로 전달한다.
3. 서버는 각 요청의 지역 변수에서 거리·순서·경로 계산에만 사용하고 응답 전에 폐기한다.
4. 완료·취소·새로고침·탭 종료 시 브라우저에서 폐기한다.
5. 장소 ID·URL과 사용자 작성 정보만 저장한다.

답변 날짜, 문의 내용, 답변 원문 보관 위치와 적용 결론은 내부 운영 기록에 남기되 공개 저장소에는 개인정보를 제거한 요약만 기록한다. 제공자 정책이나 답변 조건이 바뀌면 좌표 기반 신규 제작을 중지하고 장소·좌표 원천과 API 계약을 재검토한다.

### 지역 기준 데이터 갱신

지역 기준 데이터의 원천과 생성 규칙은 ADR-038을 따른다. 백엔드 저장소 관리자는 매월 첫 개발 주와 행정구역 개편 공지 시점에 행정표준코드 변경 공지, 법정동 코드 전체자료와 국토교통부 행정구역도 WFS의 기준일을 확인한다.

갱신이 필요하면 런타임에서 원천을 조회하지 않고 별도 변경으로 `regions.json`을 재생성한다. 변경에는 원천 URL·기준일·파일 해시, 생성 명령, 추가·폐지·명칭·상위 관계·대표 좌표 diff, 제외·분류 건수와 검증 결과를 남긴다. 코드와 경계의 기준시점 또는 식별자가 맞지 않으면 배포하지 않고 마지막 검증본을 유지한다. 원천 다운로드 파일과 경계 원문은 애플리케이션 저장소에 커밋하지 않는다.

최초 생성 기록(2026-09-17): 법정동 원천은 `국토교통부_전국 법정동_20260630` 20,561행이며 SHA-256은 `E5657D4B53A16F72E42E9C0D91E84FF2D647E70DC56B9408DA6FD057D0DA45C3`이다. 경계는 같은 날 브이월드 데이터 API의 `LT_C_ADSIDO_INFO` 16건과 `LT_C_ADSIGG_INFO` 256건을 WGS84 GeoJSON으로 조회했다. 지역 246개 중 최종 선택 161개, 장소 검색 필터 76개를 생성했으며, 도 산하 분구시는 일반구 경계를 합쳐 시 대표점을 계산했다. 원천 코드 집합, 역할, 부모, 주소 경계, 좌표 범위와 대표점의 원천 경계 내부 포함 여부를 독립 검사했다.

## 10. 외부 Client 구현 전 결정 게이트

외부 정책, 요금과 쿼터는 변경될 수 있으므로 실제 Client를 구현할 때 공식 문서를 다시 확인한다.

| Client | 구현 전 확정할 항목 |
|---|---|
| OpenAI | 모델, 구조화 출력, timeout, 재시도, 전체 예산 |
| 카카오 Local | endpoint, 필드, 반경·페이지 제한, 저장·표시 정책, 쿼터 |
| 카카오 Mobility | 자동차 endpoint, 구간 묶음, timeout, 오류, 과금·쿼터 |
| 카카오 대중교통 | 카카오맵 REST endpoint·인증 재확인, 사용자 한도, 응답 사용 조건 |
| selectionToken | 서명 알고리즘, 만료, 키 교체, payload 최소화 |
| JWT | ADR-037의 비밀번호 규칙, 1시간 access token, key 교체 설정과 User 존재 검증 구현 확인 |

기술 선택이나 계약이 바뀌면 `docs/04-api-spec.md`와 `docs/06-decisions.md`를 같은 변경 단위에서 갱신한다. 자동 테스트는 결정 후에도 Fake Client를 유지한다.

## 11. DB와 배포 절차

### 승인된 DB dependency 계약

F0-03에서는 아래 dependency만 DB 기반 목적으로 추가한다. 현재 적용된 Spring Boot dependency management BOM이 관리하는 버전을 사용하고 개별 버전을 임의로 덮어쓰지 않는다.

| Gradle scope·dependency | 목적 | 대안과 판단 |
|---|---|---|
| `implementation 'org.springframework.boot:spring-boot-starter-data-jpa'` | JPA·Hibernate·Repository와 트랜잭션 기반 | JDBC 직접 구현은 현재 학습 목표와 Entity 중심 설계에 맞지 않아 제외 |
| `implementation 'org.springframework.boot:spring-boot-starter-flyway'` | 애플리케이션 시작 시 versioned migration 실행 | Hibernate schema 생성은 migration 이력과 운영 통제를 보장하지 못해 제외 |
| `runtimeOnly 'org.flywaydb:flyway-mysql'` | Flyway의 MySQL 지원 | MySQL 사용이 확정되어 다른 DB 모듈은 추가하지 않음 |
| `runtimeOnly 'com.mysql:mysql-connector-j'` | MySQL JDBC 연결 | 운영 DB와 다른 JDBC driver는 사용하지 않음 |
| `testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'` | Spring Boot 4의 JPA 테스트 지원 | 범용 테스트 dependency만으로 조립하지 않고 공식 테스트 starter 사용 |
| `testImplementation 'org.springframework.boot:spring-boot-testcontainers'` | `@ServiceConnection` 기반 연결 정보 주입 | 동적 URL을 수동 property로 복제하지 않음 |
| `testImplementation 'org.testcontainers:testcontainers-mysql'` | MySQL 8.4 컨테이너 제공 | H2와 외부 공용 DB는 제외 |
| `testImplementation 'org.testcontainers:testcontainers-junit-jupiter'` | JUnit 생명주기에서 컨테이너 관리 | 수동 start·stop 코드로 생명주기를 분산하지 않음 |

Testcontainers는 테스트 전용이며 production runtime에 포함하지 않는다. MySQL 컨테이너 이미지는 운영 Compose와 같은 `mysql:8.4` 계열로 맞춘다.

1. 새 versioned migration을 추가한다.
2. 빈 DB와 기존 검증 DB에 migration을 적용한다.
3. 관련 테스트와 `./gradlew test`를 통과시킨다.
4. 백업·복구 방법과 이전 버전 호환성을 확인한다.
5. 배포 시 migration을 적용하고 애플리케이션을 시작한다.
6. `ddl-auto: validate`로 schema 일치만 확인한다.
7. liveness, readiness와 핵심 인증·조회 흐름을 확인한다.
8. 외부 기능은 비용 한도가 적용된 smoke 요청으로 필요한 범위만 확인한다.
9. 로그와 metric에 저장 금지 값이 없는지 점검한다.

파괴적 schema 변경은 호환 기간, 백업, 복구와 rollback 계획을 별도 변경 문서에 기록한다.

## 12. 배포 전후 체크리스트

### 배포 전

- [ ] 카카오 좌표의 일시 사용·즉시 폐기 조건이 구현과 테스트에서 검증됐는지 확인
- [ ] 운영 비밀값이 코드·Git·이미지·로그 설정에 없는지 확인
- [ ] 키의 API·origin·도메인 제한과 교체 절차 확인
- [ ] migration을 빈 DB와 검증 DB에 적용
- [ ] 자동 테스트 통과
- [ ] CORS, JWT, DB 접근 범위 확인
- [ ] 사용자별 한도와 API별 공식 무료 한도의 90% 차단 확인
- [ ] timeout, 재시도, fallback과 오류 변환 확인
- [ ] 자동차·대중교통 각각 60회/분·120회/일과 대중교통 서비스 일일 900건 차단 확인
- [ ] 경로 쿼터 사전 확보 실패 시 외부 호출 0건, 전체 구간 추정 fallback과 warning 확인

### 배포 직후

- [ ] liveness, readiness와 지역 데이터 적재 확인
- [ ] 회원가입·로그인과 소유 일정 조회 확인
- [ ] 완료·공유 일정 조회가 외부 호출 없이 동작하는지 확인
- [ ] migration 버전과 schema validate 확인
- [ ] 로그·metric에 비밀값, 원문과 좌표가 없는지 확인
- [ ] 외부 장애가 앱·DB 장애와 구분되는지 확인
- [ ] 기능별 호출량, 오류율, latency와 차단 상태 확인

### 장애 대응

- 제공자 장애 중에도 기존 완료·공유 일정 조회를 유지한다.
- 키 유출은 키 폐기와 교체를 우선하며 원문 로그를 추가로 수집하지 않는다.
- 호출 급증 시 사용자 한도와 서비스 전체 차단을 확인하고 원인을 endpoint·outcome 수준에서 조사한다.
- 정책 위반 가능성이 발견되면 좌표 기반 신규 제작 기능을 중지하고 저장 데이터와 로그에 금지 정보가 남았는지 점검한다.
