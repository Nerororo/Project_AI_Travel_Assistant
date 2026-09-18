# 구현 준비도와 현재 기준선

## 1. 문서 역할

이 문서는 실제 코드와 설정, 문서 정렬 상태, 구현 가능 여부와 차단 조건을 기록한다. 목표 요구사항이나 완료 체크리스트를 복제하지 않는다.

- 제품 범위: `docs/01-requirements.md`
- 아키텍처: `docs/02-architecture.md`
- DB·HTTP 계약: `docs/03-database.md`, `docs/04-api-spec.md`
- 결정 이유: `docs/06-decisions.md`
- 테스트·운영: `docs/08-test-strategy.md`, `docs/09-operations.md`
- 실행 순서: `docs/11-command-roadmap.md`
- 완료 판정: `docs/10-definition-of-done.md`
- 쓰기 경계: `docs/12-harness-boundaries.md`

상태 의미:

| 상태 | 의미 |
|---|---|
| 설계 확정 | 제품·기술 계약이 정해졌지만 구현 완료를 뜻하지 않음 |
| 설계 필요 | 제품 방향은 있으나 구현 전에 세부 계약을 확정해야 함 |
| 차단됨 | 외부 답변이나 선행 결정 전에는 진행하면 안 됨 |
| 구현 전 | 목표 계약은 있으나 코드가 없음 |
| 세부 계약 필요 | 큰 방향은 정했지만 구현 전 추가 결정을 해야 함 |
| 부분 구현 | 일부 코드·설정만 있고 완료 기준을 충족하지 않음 |
| 구현·검증 완료 | 코드와 적용 가능한 완료 기준을 검증함 |

## 2. 코드 기준선 (2026-09-17, F0-06 보완 완료)

| 영역 | 상태 | 확인 근거 |
|---|---|---|
| Spring Boot | 기본 골격 실행 가능 | `TravelApplication` 기동과 명세 밖 개발 endpoint의 공통 404 처리 확인 |
| Java·Gradle | 현재 골격 실행 가능 | JDK 21.0.12.1, Gradle 9.7.1, Spring Boot 4.1.1로 빌드 확인 |
| Web·Validation | dependency 존재 | webmvc·validation starter와 테스트 starter의 runtime·testRuntime classpath 확인 |
| JPA·MySQL·Flyway | 기반 구현·검증 완료 | 승인 dependency와 profile 설정을 적용하고 MySQL 8.4에서 Flyway 실행 후 `ddl-auto: validate` 통과 |
| Docker MySQL | 테스트 연결 검증 | Docker Engine과 Testcontainers MySQL 8.4 연결 성공, local Compose의 수동 연결·배포 검증은 남음 |
| 인증·보안 | U1 범위 구현·검증 완료 | 회원가입·JWT 로그인, 공개 API 경계, MySQL 호출 카운터와 requestId 실행 상태의 회귀 점검 완료, 회원 탈퇴·TravelPlan 소유권은 후속 작업 |
| 지역 | G1 단계 구현·검증 완료 | 출처가 확인된 `regions.json` 246개와 최종 지역 161개의 공식 경계 기반 `searchBounds`, 시작 검증, 메모리 Catalog와 결정적 직접 검색 API 및 AI용 최종 선택 가능 지역 공개 계약을 전체 테스트로 검증했으며 완료 일정 snapshot은 후속 T1 책임 |
| AI | A1 단계 구현·검증 완료 | DTO·Service·Fake와 prod·smoke 실제 Responses API Client에 인증된 지역·메뉴 HTTP API, 기능별 사용자 한도와 requestId 처리를 연결하고 AI 단계 전체 DoD와 한도 경계를 검증했다. 지역 추천 브라우저 연결은 W1-01B에서 완료했고 메뉴 분석 연결은 W1-02A 후속 작업 |
| Place | 하네스만 존재 | Client·Service·DTO 코드 없음 |
| Route | 하네스만 존재 | 알고리즘·Client·Service 코드 없음 |
| Recommendation | 하네스만 존재 | Service·정책 코드 없음 |
| TravelPlan | 하네스만 존재 | Entity·Repository·Service·DTO 코드 없음 |
| DB migration | User·호출 카운터·requestId schema 구현·검증 | V1 `users`, V2 `api_usage_counters`, V3 `request_executions`를 MySQL 8.4에 적용하고 Hibernate validate 통과 |
| 자동 테스트 | 인증·지역·AI API 통합 검증 | 전체 135개 통과, JWT·호출 한도와 정확한 AI 분·일 경계·10분 requestId 상태·지역 기준 데이터·AI DTO와 실제 Client 요청·응답·재시도 및 지역·메뉴 API 계약을 검증 |
| 화면 | W1-00·W1-01A~C 구현·검증 완료 | 공통 app shell·6개 view·8단계 Workspace 골격, 회원가입·로그인 API adapter와 지역 직접 검색·정확히 3개인 AI 추천 후보 선택을 연결하고 서버 TTL 기반 메모리 인증·만료·보호 화면을 검증, 장소·일정 API 연결은 후속 범위 |

F0-01에서 `./gradlew test --rerun-tasks`와 실제 애플리케이션 기동은 통과했다. 현재 성공은 Web 골격의 실행 가능성만 뜻한다. `application.yml`의 JPA 설정만으로 JPA나 DB 연결이 구현된 것은 아니며, 관련 dependency가 classpath에 없으므로 현재 테스트와 기동 과정에서는 datasource 설정과 `${DB_PASSWORD}`도 사용되지 않는다. local·test·prod·smoke profile 파일 역시 아직 없다.

F0-03에서 Spring Boot 관리 버전의 JPA·MySQL·Flyway·Testcontainers dependency와 local·test·prod·smoke profile 기반을 적용했다. Testcontainers가 제공한 빈 MySQL 8.4에서 Flyway가 schema history를 만든 뒤 Hibernate `ddl-auto: validate`까지 통과했다. 현재 Entity와 production migration이 0개인 상태를 검증한 것이므로 업무 schema 구현 완료를 뜻하지 않는다.

F0-04A에서 공통 오류 DTO의 고정 필드, validation reason, 인증·인가·not found·409·422·429·503·500 변환 계약을 확정했다. F0-04B에서 공통 DTO·오류 코드·비즈니스 예외·전역 Exception Handler를 구현하고 validation, 잘못된 JSON, 상태별 비즈니스 예외, 429 header, 안전한 500 응답을 MockMvc로 검증했다. 실제 Spring Security의 401·403 연결은 U1 구현 범위다.

F0-05 회귀 점검에서 발견한 미매핑 URL·정적 리소스의 500 오분류는 F0-04C에서 보완했다. 일반 404 `RESOURCE_NOT_FOUND` 계약과 Spring MVC 예외 변환을 추가하고, 내부 요청 경로·상세를 노출하지 않는 고정 오류 DTO를 MockMvc로 검증했다. 보완 후 전체 테스트도 통과했으며 F0-05 완료 판정은 별도 점검 Task에서 수행한다.

F0-05 재점검에서 MySQL 8.4 Testcontainers를 포함한 전체 21개 테스트가 실패·오류·건너뜀 없이 통과했다. 운영·smoke DB 자격 증명은 기본값 없는 환경 변수로만 주입되고, 소스·설정·fixture·문서에서 실제 비밀값과 자격 증명 패턴이 발견되지 않았으며 `git diff --check`도 통과했다. 이 판정은 F0 기반과 공통 오류 처리 범위에 한정하고, 인증·보안과 실제 외부 Client의 운영 검증은 후속 작업에서 수행한다.

`AGENTS.md`는 구현 경계이며 기능 코드가 아니다. 도메인 하네스가 존재해도 해당 기능을 구현됨으로 표시하지 않는다.

## 3. 문서·하네스 정렬 결과

현재 기준 문서와 하네스에는 확정된 제품 계약과 경로별 책임을 반영했다. `docs/11-command-roadmap.md`는 D0 게이트의 완료 여부만 표시하며 개별 문서 편집 이력을 관리하지 않는다. 이 정렬 상태는 기능 구현 완료를 뜻하지 않는다.

## 4. 제품 결정 준비도

| 주제 | 설계 상태 | 구현 상태 | 근거 또는 다음 조치 |
|---|---|---|---|
| 국내 범위 | 확정 | 기준 데이터 구현·검증 | 서울·광역시·세종은 자체 선택, 도·특별자치도는 하위 시·군 선택, 광역자치단체의 구·군은 검색 필터, 읍·면·동·해외 제외 |
| 지역 직접 검색·AI 추천 | 구현·검증 | 같은 `regions.json`의 최종 선택 가능 지역만 AI에 제공하고 인증·한도·requestId가 적용된 HTTP API와 직접 검색·정확히 3개인 AI 후보 선택 브라우저 연결 구현 | 실제 Spring 서버와 브라우저의 end-to-end smoke는 별도 운영 검증 |
| 이동수단 | 확정 | 구현 전 | 일정당 CAR 또는 PUBLIC_TRANSIT 하나 |
| 순수 경로 | 확정 | 구현·검증 완료 | WGS84 Coordinate·Haversine·Nearest Neighbor·고정 양 끝 경계 2-opt·이동수단별 10분 단위 시간 추정·기하 중앙값·메도이드를 최대 하루 입력과 7일 고정 경계 조합으로 회귀·성능 검증 |
| 실제 경로 호출 시점 | 확정 | 구현 전 | 최종 후보의 인접 구간만 조회 |
| 이동 시간 | 확정 | 구현 전 | 제공자 예상 초를 10분 단위로 올림 |
| 체류 시간 | 확정 | 구현 전 | 유형 미노출, 30~480분의 10분 단위 |
| 사용자 장소 이름 | 확정 | 구현 전 | 빈 입력에서 직접 작성, trim 후 1~50자 |
| 시간 초과 | 확정 | 구현 전 | 저장 차단, 자동 삭제·체류 축소 없음 |
| 완료 후 편집·조회·공유 | 확정 | 구현 전 | 제한 텍스트 편집, 외부 호출·지도 없는 조회 |
| 인증·회원 탈퇴 | 확정 | 부분 구현 | 회원가입·1시간 access JWT 로그인·보호 API 인증 구현 완료, 탈퇴와 종속 데이터 삭제는 후속 작업 |
| 호출 한도·저장소 | 확정 | 구현·검증 완료 | MySQL 공유 사용자·서비스 카운터와 10분 requestId PROCESSING·SUCCESS 상태, 원자 선점·중복 차단 구현 완료 |
| 이동시간 출처 | 확정 | 구현 전 | 숫자만 저장, 생성 warning 비영속, 집계 metric |
| 카카오 좌표 활용 계약 | 정책 확인 완료 | 구현 전 | 2026-09-11 DevTalk 답변과 ADR-028 |
| 운영 배포 | 차단 | 구현 전 | 기능·테스트·운영 검증 필요 |

카카오 답변 반영은 완료됐다. 이후 모든 좌표 기반 구현은 ADR-028의 일시 사용·즉시 폐기 조건을 따라야 한다.

## 5. 구현 전 확정할 세부 계약

| 항목 | 로드맵 시점 | 기록 위치 |
|---|---|---|
| 테스트 DB 방식과 JPA·MySQL·Flyway dependency | 결정 완료 | MySQL 8.4 Testcontainers, Spring Boot 관리 버전, `docs/08` 3절과 `docs/09` 11절 |
| JWT 만료·재발급·로그아웃, User 삭제 | 결정 완료 | ADR-037, `docs/03` User 삭제, `docs/04` 1~2절, `docs/09` 3·10절 |
| 지역 공공데이터 출처·기준일·생성 절차 | 결정 완료 | ADR-038, `docs/09` 9절 지역 기준 데이터 갱신 |
| CAR·PUBLIC_TRANSIT 초기 추정 계수 | 결정 완료 | ADR-040, 구현·단위 테스트는 R1-02~06 |
| OpenAI 모델·전체 예산 | 결정 완료 | ADR-039의 `gpt-5.6-luna`, 월 USD 5 계획과 OpenAI project USD 4 hard spend limit |
| selectionToken 서명·만료·키 교체 | P1-02 | 보안 ADR·API·운영 |
| 자동차 요청 단위·공식 쿼터 | R2-01 | API·운영 |
| AI 메뉴·식사·음식점 흐름 | 확정 | 메뉴 1~5개 사용자 확정, 식사 60분·한쪽 여유 15분, estimate 후 지도 선택, 저장 후 재검색 없음 |
| 공유 토큰 해시·만료 | T1-01 | DB·API·보안 ADR |

미확정 값을 임시 상수, 넓은 nullable, 가짜 운영 데이터나 테스트 생략으로 우회하지 않는다.

## 6. 문서 정렬 후 진행 가능한 범위

카카오 답변과 무관하게 다음 순서로 진행할 수 있다.

1. `F0-01`: 실제 코드·설정·dependency 실행 가능성 감사
2. `F0-02~05`: 개발·DB·오류 처리 기반
3. `U1`: 인증과 사용자별 한도 기반
4. `G1`: 국내 지역 기준과 직접 검색
5. `A1`: 지역 추천·메뉴 분석 AI
6. `R1`: 순수 거리·방문 순서 알고리즘
7. `C1`: Place·Route Client 인터페이스와 Fake

`K0-02A`가 완료됐으므로 C1까지 마친 뒤의 백엔드 주 흐름은 `P1 → R2 → S1 → T1 → Q1` 순서를 따른다. 화면은 W1 전체를 T1 뒤로 미루지 않고 `docs/11-command-roadmap.md`의 선행 조건을 충족한 작업부터 하나씩 끼워 진행한다. `W1-06`만 모든 화면 연결 뒤, Q1 전에 수행한다.

## 7. 현재 작업 상태

F0·U1·G1·A1의 현재 범위와 W1-00·W1-01A~C가 구현·검증됐다. R1-01에서 좌표·Haversine·기하 중앙값·메도이드와 이동수단별 초기 시간 추정 계약을 ADR-040으로 확정했다. R1-02에서 WGS84 좌표 값 객체와 평균 지구 반지름 기반 Haversine 거리를 순수 Java로 구현하고 0 거리·대칭성·알려진 거리 오차·좌표 범위와 비유한 값 거절을 단위 테스트로 검증했다. R1-03에서 Nearest Neighbor의 입력·출력·시작점·열린 경로·안정 키 동률·실패 조건과 `O(n²)` 시간·`O(n)` 추가 공간 계약을 ADR-040에 확정했다. R1-04에서 안정 키·좌표 입력과 명시적 시작점을 사용하는 Nearest Neighbor 열린 경로를 순수 Java로 구현하고 빈 목록·단일 장소·전체 장소 1회 방문·시작점 보존·입력 순서와 무관한 동률 결정성·입출력 불변·잘못된 입력 거절을 단위 테스트로 검증했다. R1-05에서 고정 출발·도착 경계를 제외한 관광지 구간만 반전하는 결정적 2-opt를 구현했다. 경로 비악화, 양 끝 경계·장소 집합 보존, 개선 없음·1,000회 반복 상한, 입력·결과 불변성과 동일 호텔 좌표의 서로 다른 시작·도착 안정 키를 단위 테스트로 검증했다. R1-06에서 이동수단이 고정된 불변 정책과 Haversine 이동시간 계산기를 구현했다. 자동차는 우회계수 1.30·평균속도 60km/h, 대중교통은 1.40·50km/h를 사용하며 0km는 0분, 양의 거리는 최소 10분으로 10분 단위 올림한다. 정책 범위·비유한 값·정확한 경계·이동수단 분리를 단위 테스트로 검증했다. R1-06A에서 정렬된 입력을 지역 equirectangular km 평면으로 투영하는 modified Weiszfeld 기하 중앙값과 Haversine 거리 합이 최소인 실제 입력 메도이드를 구현했다. 빈·단일 입력, 대칭 입력, 중복 좌표, 수렴·반복 상한, 안정 키 동률과 입력 순서 독립성을 단위 테스트로 검증했다. R1-07에서는 고정 출발·도착 경계와 관광지 5개의 최대 하루 입력을 두 이동수단으로 조합해 결정성, 장소·경계 보존, 경로 비악화, 10분 단위 시간, 기하 중앙값·메도이드를 함께 회귀 검증했다. 로컬 1,000회 측정은 26,012,200ns였고 대표 입력의 2-opt 거리는 20.031509km에서 19.055881km로 4.87% 줄었다. 추가 7일 회귀에서는 첫날 역→장소→호텔, 중간 날 호텔→장소→같은 호텔, 마지막 날 호텔→장소→역 경계를 보존했고, 날짜별 장소 6개의 전수조사 결과 표본 모두 Haversine 최단거리와 일치했다. 7일 계산 1,000회는 71,500,000ns였으며 이는 날짜 배분·식사 시간표·실제 경로 최적성을 포함하지 않는다. 루트 `test.ps1` 전체 186개 테스트와 `git diff --check`가 통과해 R1 범위를 완료했다. C1-01에서 카카오 장소 검색의 요청·응답·오류 계약을 `place/client`에 추가하고, 정상 빈 결과와 timeout·연결 실패·제공자 오류·잘못된 응답을 구분했다. 제공자 표시명·주소·좌표·카테고리를 요청 범위 임시 후보에만 두어 API·저장 모델과 분리했으며, 계약 단위 테스트와 루트 `test.ps1` 전체 193개 테스트 및 `git diff --check`가 통과했다. 다음 백엔드 주 작업은 `C1-02`다. 활성 구현 작업과 이후 실행 순서는 `docs/11-command-roadmap.md`를 따른다. `A1-08`은 발견 사항을 기록한 미실행 후속 작업이다.

C1-02에서 테스트 전용 `FakeKakaoPlaceClient`와 생성자 주입 기반의 얇은 `PlaceService` 골격을 추가했다. 성공·정상 빈 결과·timeout·4xx·5xx를 재현하고 각 실패에서 암묵적 재시도 없이 한 번만 Client를 호출하는지 검증했다. 실제 `KakaoPlaceClient` Bean이 아직 없는 단계이므로 `PlaceService`의 Spring Bean 등록은 실제 Client 구성과 함께 수행하도록 유보했다. 루트 `test.ps1` 전체 202개 테스트와 `git diff --check`가 통과했으며 다음 백엔드 주 작업은 `C1-03`이다.

C1-03에서 공통 `RouteClient`와 자동차·대중교통 전용 하위 인터페이스를 추가하고 요청 범위의 단일 인접 좌표 구간 계약을 정의했다. 성공은 제공자 예상 초만 가진 `Found`, 정상 경로 없음은 예외가 아닌 `NotFound`, 요청·인증·한도·timeout·연결·5xx·잘못된 응답은 외부 원문 없는 정규화 실패로 분리했다. 재시도 가능한 일시적 기술 장애도 요청·계약 실패와 구분했으며, 루트 `test.ps1` 전체 210개 테스트와 `git diff --check`가 통과했다. 다음 백엔드 주 작업은 `C1-04`다.

C1-04에서 테스트 전용 자동차·대중교통 Fake Route Client와 생성자 주입 기반의 얇은 `RouteService` 골격을 추가했다. 일정의 `TravelMode`에 따라 해당 Client 하나만 호출하고 성공·정상 경로 없음·timeout·연결 실패·5xx 정규화 결과를 재시도와 fallback 없이 그대로 전달하는 계약을 검증했다. 실제 Route Client Bean이 아직 없는 단계이므로 `RouteService`의 Spring Bean 등록은 실제 Client 구성과 함께 수행하도록 유보했다. 루트 `test.ps1` 전체 217개 테스트와 `git diff --check`가 통과했으며 다음 백엔드 주 작업은 `C1-05`다.

C1-05에서 2026-09-18 카카오 공식 문서를 기준으로 Local·자동차·대중교통 endpoint, 인증, 요청·응답, 쿼터와 요금을 감사했다. 대중교통 경로 조회는 2026-07-21 카카오맵 REST API에 정식 추가되어 제품 방향과 일치하고, 1,000건/일 무료 쿼터의 90%인 900건 차단선도 일치한다. 다만 Local의 20km 밖 도시 전체 공간 검색, 자동차 `result_code`별 정규화, 대중교통 복수 후보 선택·출발시각 없는 시간 의미·상태 매핑이 확정되지 않아 실제 Client 구현 게이트는 차단 상태다. 기존 API·ADR과 코드는 수정하지 않았으며 별도 `C1-05A`에서 계약을 정렬해야 한다.

C1-05A 결정 1에서 모든 최종 선택 가능 지역에 공식 WGS84 행정경계 기반 `searchBounds`를 생성하기로 확정했다. 관광지는 대표 좌표 20km로 먼저 검색하고 결과 부족 또는 사용자 요청 시 bounds를 카카오 `rect`의 `minLongitude,minLatitude,maxLongitude,maxLatitude` 순서로 전달해 공식 지역명 결합 검색을 수행하며, 주소 행정구역 검증·장소 ID 중복 제거·외부 호출별 사용량 집계를 적용한다. 기준 데이터와 Loader는 후속 `G1-06`에서 구현·검증을 완료했고, radius/rect Client DTO는 `C1-05B`, 실제 Local 호출은 `P1-04`로 분리했다.

C1-05A 자동차 결정 1에서 카카오모빌리티 `result_code=1`을 `RouteResult.NotFound`로 확정했다. 재시도·Haversine fallback·자동 장소 삭제·자동 이동수단 변경 없이 422로 전체 저장을 차단하고, 브라우저 작성 상태를 유지한 채 사용자가 조정 후 전체 경로를 다시 검증한다. 실패 구간 `details`는 모든 위치 유형과 반복 방문에 공통으로 적용할 수 있도록 `date + moveOrder + travelMode`로 확정했으며 좌표·장소명·카카오 ID·제공자 원문은 포함하지 않는다. 구현은 `T1-06A`, 화면 처리는 `W1-03B`로 분리했다.

C1-05A 자동차 결정 2에서 Routy 자동차 요청은 `waypoints` 없이 인접 구간의 `origin`과 `destination`만 사용한다고 고정했다. 따라서 경유지 관련 `result_code=101·107`은 정상 흐름에서 발생할 수 없는 `RouteClientFailure.INVALID_RESPONSE`로 확정했다. 재시도·fallback 없이 503으로 저장을 차단하고 사용자 장소 문제로 안내하지 않는다.

C1-05A 자동차 결정 3에서 시작·도착 지점 주변 도로를 탐색할 수 없는 `result_code=102·103`을 `RouteResult.NotFound`로 확정했다. 유효한 인접 구간 요청의 정상적인 경로 없음으로 보고 재시도·fallback 없이 422로 저장을 차단하며, 실패 원인을 물리적 도로 부재나 도보·선박 필요로 단정하지 않는다.

C1-05A 자동차 결정 4에서 출발지와 도착지가 5m 이내인 `result_code=104`를 `RouteResult.Found(0)`으로 확정했다. 일정 생성을 계속하고 해당 `MOVE`를 0분으로 유지하며, 10분 올림과 고정 buffer를 적용하지 않는다. 호출 전에 Haversine으로 5m를 판정해 외부 호출을 생략하지 않고 실제 104 응답에만 적용한다.

C1-05A 자동차 결정 5에서 시작·도착 지점 주변 교통 장애인 `result_code=105·106`을 `RouteResult.NotFound`로 확정했다. 제공자 장애가 아닌 현재 자동차 구간의 정상적인 경로 없음으로 보고 재시도·fallback 없이 422로 저장을 차단하며 구체적인 사고·통제 원인은 사용자에게 노출하지 않는다. 이로써 자동차 `result_code=1, 101~107` 매핑은 확정됐고 대중교통 후보·시간 의미·상태 매핑만 남았다.

C1-05A 대중교통 결정 1에서 `status=OK`의 첫 후보 `routes[0].properties.totalTime`만 사용하고 초 단위 시간을 10분 단위로 올리기로 확정했다. 공식 문서가 첫 후보의 추천·최단 의미를 보장하지 않는 한계를 수용하고 최소시간·환승·요금·거리 재정렬은 하지 않는다. 첫 후보가 없거나 시간이 누락·음수이면 뒤 후보를 사용하지 않고 `INVALID_RESPONSE`로 처리한다.

C1-05A 대중교통 결정 2에서 `totalTime`을 API 조회 시 반환된 일정 계획용 예상 이동시간으로 확정했다. 여행 날짜·출발 시각을 요청하지 못하므로 미래 여행일의 운행 여부·배차·막차·지연·실제 소요시간을 보장하지 않고, 제작·완료·공유 화면은 모두 `예상 이동시간`으로 표시하며 완료 조회나 여행 당일 자동 재계산도 하지 않는다.

C1-05A 대중교통 결정 3에서 `STARTNODES_NULL`·`ENDNODES_NULL`·`NO_RESULTS`는 `RouteResult.NotFound`, `EQUAL_POINTS`는 `RouteResult.Found(0)`, `INVALID_REQUEST`는 `RouteClientFailure.INVALID_REQUEST`로 확정했다. 알 수 없는·누락된 status와 손상된 `OK` 응답은 `INVALID_RESPONSE`로 처리한다. 정상 경로 없음은 422, request·response 계약 오류는 재시도·fallback 없는 503으로 저장을 차단한다. 이로써 C1-05A의 계약 설계는 완료됐으며 실제 Client·DTO 구현과 자동 검증은 후속 Task에서 수행한다.

C1-05B에서 `PlaceSearchRequest`가 WGS84 중심점·반경 또는 사각형 bounds 중 정확히 하나만 받도록 확장했다. 중심점·반경의 부분 입력과 두 공간 입력의 동시 사용을 거절하고, 공식 Local 계약의 radius 0~20,000m·page 1~45·size 1~15 및 유한 좌표·최소/최대 bounds를 생성 시 검증한다. `around`·`withinBounds` 팩토리와 Fake·Service 테스트로 radius/rect 요청 객체, page와 외부 호출 1회 단위가 변형되지 않음을 고정했다. 실제 Kakao `rect` 직렬화와 HTTP 호출은 P1-04 책임이며, 루트 `test.ps1` 전체 222개 테스트가 실패·오류·건너뜀 없이 통과했다.

U1-01에서 비밀번호·JWT·공개 endpoint·User 삭제 계약을 ADR-037로 확정했다. 이는 설계 완료이며 User Entity, migration, 회원가입과 Spring Security·JWT 구현은 각각 U1-02~04에서 검증해야 한다.

U1-02에서 User Entity·Repository와 V1 `users` migration을 구현했다. MySQL 8.4에서 production migration 적용, Hibernate `ddl-auto: validate`, Repository 저장·조회, 이메일 대소문자 UNIQUE와 `password_hash`만 존재하는 schema를 통합 테스트로 검증했다. 회원가입 시 이메일 정규화·해시 생성·중복 오류 변환은 U1-03, 인증과 JWT는 U1-04 범위다.

U1-03에서 회원가입 Service와 `POST /api/users`를 구현했다. 이메일 소문자 정규화, BCrypt strength 12 해시 저장, 선조회와 DB UNIQUE 경쟁 상황의 중복 이메일 409 변환, 비밀번호 code point·UTF-8 byte·제어 문자 validation을 Service·HTTP·MySQL 8.4 통합 테스트로 검증했다. Spring Security 필터와 JWT 로그인은 U1-04 범위다.

U1-04에서 `POST /api/auth/login`과 stateless Spring Security 필터 체인을 구현했다. JWT는 환경 설정으로 주입하는 key ID별 최소 256-bit HMAC key 중 active key로만 발급하고, `sub`·`iss`·`iat`·`exp`·`jti`, HS256 allowlist, key ID, 서명과 User 존재 여부를 보호 요청마다 검증한다. 로그인 성공·동일 401 실패 응답, 1시간 수명, 만료·변조·알 수 없는 key, 공개 endpoint의 HTTP method 경계와 보호 API 차단을 단위·HTTP·MySQL 8.4 통합 테스트로 검증했으며 전체 47개 테스트가 통과했다. 회원 탈퇴, 호출 한도와 TravelPlan 소유권은 후속 작업 범위다.

U1-05A에서 V2 `api_usage_counters` migration과 공개 `ApiUsageService`를 구현했다. 사용자별 분·일 창과 기능별 서비스 전체 일 창을 MySQL에서 공유하고, `Asia/Seoul` 자정 기준 창 계산, 여러 호출 수의 조건부 원자 확보, 실패 시 전체 rollback과 retryAfterSeconds 계산을 적용했다. 카카오 공식 일일 쿼터의 90%인 장소 90,000·자동차 9,000·대중교통 900 서비스 차단선을 정책 한 곳에 두었고, AI 서비스 전체 예산은 모델·가격 확정 전까지 설정하지 않았다. MySQL 8.4에서 migration·schema 저장 금지 열, 경계값, 부분 차감 방지와 병렬 다중 인스턴스 상당 경쟁을 검증했으며 전체 56개 테스트가 통과했다. requestId 실행 상태는 U1-05B 범위다.

U1-05B에서 V3 `request_executions` migration과 공개 `RequestExecutionService`를 구현했다. 사용자·기능·UUID requestId별 PROCESSING·SUCCESS 상태와 10분 만료만 MySQL에 저장하며, requestId 선점을 호출량 확보보다 먼저 커밋해 처리 중·성공 중복이 외부 실행·저장·호출량 차감을 반복하지 않도록 했다. 실패 해제, 만료 재선점, 이전 lease의 새 실행 변경 차단, 제한 batch 만료 삭제와 User FK cascade를 구현했다. MySQL 8.4 병렬 경쟁에서 정확히 한 실행만 선점되고 나머지가 `REQUEST_IN_PROGRESS`로 차단되는 것을 검증했으며 전체 68개 테스트가 통과했다. 실제 기능 endpoint 연결은 각 A1·P1·R2·T1 작업 범위다.

U1-06에서 인증·보안 회귀를 점검했다. `POST /api/users`, `POST /api/auth/login`, `GET /api/shared/travel-plans/{shareToken}`만 공개하고 그 밖의 `/api/**` 요청은 인증을 강제하는 HTTP method 경계, 로그인 성공·동일 401 실패, JWT 만료·변조·알 수 없는 key·삭제된 사용자 차단, 비밀값 비노출, 호출 한도와 requestId 중복 방지를 관련 47개 테스트와 전체 68개 테스트로 재검증했다. 전체 테스트는 실패·오류·skip 없이 통과했고 `git diff --check`도 통과했다. 회원 탈퇴와 TravelPlan 소유권, 실제 배포 환경의 origin·도메인 제한·health·smoke 검증은 각 후속 작업과 운영 준비 범위다.

W1-00에서 `Routy/INTEGRATION.md`의 신규 화면 원칙을 공통 app shell과 page-level view 골격으로 구현했다. 랜딩·인증·Journey Workspace·내 여행·완료 일정·공유 일정의 정보 구조, 팝업이 아닌 8단계 제작 흐름, 초기·로딩·빈 결과·오류 상태를 만들었으며 실제 API·브라우저 저장소·가짜 성공 처리는 연결하지 않았다. Node 정적 검사와 7개 화면 골격 테스트, 390px 모바일 overflow 측정, 실제 Tab 포커스 순서, 데스크톱·모바일 렌더링, 전체 68개 Gradle 테스트와 `git diff --check`를 통과했다. 실제 인증·지역·장소·일정 API 연결은 W1-01A 이후 작업 범위다.

W1-01A에서 회원가입·로그인 화면을 `POST /api/users`, `POST /api/auth/login` 계약에 연결했다. 서버 성공 뒤에만 회원가입 완료·보호 화면 진입을 처리하고 validation·중복 이메일·401·네트워크 실패를 안전한 문구로 표시한다. JWT는 현재 탭 메모리에만 보관하며 로그아웃·만료·pagehide 때 인증 및 작성 골격 상태를 폐기하고, 중복 제출·취소 뒤 늦은 응답·다른 세션의 오래된 401을 차단한다. Node 순수 테스트 20개와 Chromium 브라우저 테스트 9개 시나리오(상위 테스트 포함 총 Node 30개), 전체 Gradle 테스트 68개가 실패·오류·skip 없이 통과했다. 데스크톱·390px 모바일 캡처, Tab·Shift+Tab·Enter, reduced motion, DOM·console 비밀값 비노출과 브라우저 저장소 부재를 확인했고 `git diff --check`도 통과했다. 브라우저는 격리 HTTP fake를 사용했으며 실제 Spring 서버와 브라우저를 연결한 end-to-end smoke는 수행하지 않았다. 후속 보호 API와 장소 메모리 모듈은 이 인증 수명 계약에 연결해야 한다.

W1-01B에서 지역 직접 검색과 AI 지역 추천을 Journey Workspace의 첫 단계에 연결했다. 직접 검색은 서버의 `selectable`·`placeSearchFilterable`을 그대로 사용해 최종 여행 지역과 장소 검색 필터용 구·군을 구분하고, AI 추천은 Bearer 인증과 요청별 UUID `Idempotency-Key`를 전달해 중복 없는 정확히 3개 후보일 때만 선택할 수 있게 했다. 선택 지역은 현재 탭 메모리에만 유지하며 요청 중복, 단계 이동·로그아웃·인증 만료 뒤 늦은 응답, 서버 원문 노출을 차단한다. Node 순수 테스트 23개와 Chromium 브라우저 테스트 11개 시나리오가 통과했고 데스크톱·390px 모바일 렌더링, 브라우저 저장소 부재, 루트 `test.ps1`의 전체 135개 Gradle 테스트와 `git diff --check`를 확인했다. 브라우저는 격리 HTTP fake를 사용했으며 실제 Spring 서버와 브라우저를 연결한 end-to-end smoke는 별도 운영 검증으로 남는다.

W1-01C에서 브라우저 로그인 성공 판정을 고정 3600초 비교에서 서버 `expiresInSeconds` 계약 기반으로 정렬했다. 유한한 양의 정수이며 안전한 절대 만료 시각으로 계산 가능한 TTL만 수용하고 요청 시작 시각을 기준으로 만료를 계산한다. 브라우저 단일 타이머 한계보다 긴 TTL은 남은 시간을 분할 예약하며 이전 세션 callback이 새 세션을 만료시키지 않는다. Node 순수 테스트 26개와 Chromium 브라우저 테스트 12개 시나리오, 루트 `test.ps1`의 전체 135개 Gradle 테스트와 `git diff --check`가 통과했다.

G1-02에서 법정동 코드와 브이월드 행정구역 경계를 대조해 정적 `regions.json` 246개를 구축했다. 최종 선택 지역 161개와 장소 검색 필터 76개를 분리하고, 광주는 사용자 표시 지역과 `전남광주통합특별시` 주소 경계를 분리했으며 수원 등 도 산하 분구시는 시만 선택 가능하게 유지했다. 원천 코드 집합·역할·부모·주소 경계·좌표 범위·대표점의 경계 내부 포함을 독립 검증했고 전체 Gradle 테스트와 `git diff --check`가 통과했다. Java Loader와 애플리케이션 시작 시 검증, 검색 Service·API는 각각 G1-03·G1-04 범위다.

G1-03에서 Region 불변 값 객체와 정적 데이터 Loader·메모리 Catalog를 구현했다. 애플리케이션 시작 시 schema version, 출처 참조, ID·필수값 중복과 누락, 타입별 선택·검색 필터 역할, 부모 존재·자기 참조·순환 관계, 주소 경계와 대표 좌표 범위를 검증하며 잘못된 데이터로 시작하지 않는다. `regionId`는 공공 코드에서 최초 파생됐더라도 구조를 해석하지 않는 Routy 소유의 안정적인 식별자로 취급하고 `KR-GWANGJU-URBAN` 같은 논리 ID도 허용한다. 실제 246개 적재와 오류 사례를 자동 테스트했고 격리 Testcontainers MySQL을 포함한 전체 테스트가 통과했다. 직접 검색·정렬과 지역 HTTP API는 G1-04 범위다.

G1-04에서 공식 이름·짧은 이름·별칭을 정확 일치, 접두 일치, 부분 일치 순으로 검색하고 동률은 표준 이름·상위 지역 이름·regionId 순으로 고정했다. 응답은 상위 표시 이름과 `parentRegionId`, 타입, 최종 선택 가능 여부, 장소 검색 필터 가능 여부를 분리하며 결과가 없으면 빈 배열을 반환한다. 인증된 `GET /api/regions`의 성공·빈 결과·필수 query·공백 query와 무인증 401을 Service·Controller·통합 테스트로 검증했다. AI 지역 추천 연결과 지역 단계 전체 DoD 점검은 각각 A1-04와 G1-05 범위다.

G1-05에서 지역 단계 DoD를 점검하고 실제 데이터의 161개 최종 선택 지역, 76개 장소 검색 필터, 9개 상위 탐색 항목을 회귀 테스트로 고정했다. 광주 5개 구의 검색 필터 역할, 세종의 1단계 전용 주소 경계, 수원 일반구의 주소 경계 전용 포함, 모든 항목의 1단계 주소 경계와 국내 범위도 함께 검증했다. Loader의 주소 경계 누락 거절 테스트를 보강했고 루트 `test.ps1`에서 Testcontainers MySQL 8.4를 포함한 전체 91개 테스트가 통과했다. 같은 허용 목록을 사용하는 AI 연결과 완료 일정의 `regionId`·표시 이름 snapshot은 각각 A1·T1 단계에서 검증한다.

G1-06에서 G1-02와 동일한 해시의 브이월드 WGS84 경계 원본으로 최종 선택 지역 161개의 `searchBounds`를 생성했다. 광주는 5개 구, 수원 등 일반구가 있는 시는 모든 구성 구 경계의 합집합에서 최소·최대 좌표를 계산했고, 바깥 방향 6자리 정밀도로 1,279,873개 원천 경계점이 모두 포함됨을 독립 검증했다. Loader는 최종 지역의 bounds 누락·비유한 값·대한민국 운영 범위 이탈·최소/최대 역전·대표점 미포함과 비선택 지역의 불필요한 bounds를 시작 시 거절한다. 지역 회귀 테스트와 루트 `test.ps1` 전체 219개 테스트가 실패·오류·건너뜀 없이 통과했다.

A1-01에서 지역 추천·메뉴 분석 요청·응답 DTO와 `AiClient` 계약, local·test 전용 Fake와 두 Spring Service를 구현했다. 지역 추천은 서버 허용 목록 안의 중복 없는 정확히 3개 ID와 1~200자 이유만 허용하고, 메뉴 분석은 중복 없는 1~5개 메뉴·검색어·이유와 요청에 포함된 선택적 대상 관광지만 허용한다. 계약 위반은 원문과 상세를 노출하지 않는 `AI_RESPONSE_INVALID`로 변환하며 DTO trim·validation과 Fake·Service 경계값을 자동 테스트했다. 실제 OpenAI 공식 계약 감사와 HTTP Client, 지역 Catalog·인증·한도·requestId·Controller 연결 및 메뉴 재시도는 A1-02~05 범위다.

A1-02에서 공식 OpenAI 문서를 감사해 ADR-039를 확정했다. 공통 Responses API에 `gpt-5.6-luna`, reasoning effort `none`, `store: false`, strict JSON Schema, 지역 512·메뉴 768 output token 상한을 사용한다. 연결 3초·전체 15초 안에서 기술 장애만 최대 한 번 재시도하고, 월 USD 5 계획 예산에 OpenAI project USD 4 hard spend limit을 둔다. Routy에는 비용·token usage·서비스 전체 일일 카운터를 추가 저장하지 않는다. 실제 HTTP Client와 설정·응답 매핑은 A1-03, 사용자 한도·requestId·API 연결은 A1-04~05 범위다.

A1-03에서 Java 21 `HttpClient`를 재사용하는 prod·smoke 전용 `OpenAiClient`를 구현했다. 지역 추천과 메뉴 분석은 공통 transport 위에서 서로 다른 instructions·strict JSON Schema·출력 token 상한과 응답 DTO 매핑을 사용한다. 요청의 Bearer 인증, `store: false`, reasoning effort `none`, 도구·metadata·conversation 미사용, refusal·incomplete·빈 output·필수 필드 누락 거절, 400·소진 quota 무재시도와 일시적 5xx 1회 재시도, 전체 timeout을 실제 OpenAI 없는 로컬 fake HTTP 테스트로 검증했다. local·test는 기존 Fake를 유지하며 전체 108개 테스트가 통과했다. 사용자 한도·requestId와 지역·메뉴 HTTP endpoint 연결, 메뉴 구조 오류 재시도는 A1-04~05 범위다.

A1-04에서 인증된 `POST /api/ai/regions/recommend`와 필수 UUID `Idempotency-Key` 계약을 구현했다. Region 공개 Service는 같은 `regions.json`에서 최종 선택 가능한 국내 지역만 AI 전달 DTO로 제공하고 상위 지역 표시 이름을 결합한다. AI Service는 외부 호출 전에 `AI_REGION_RECOMMENDATION`의 사용자별 2회/분·10회/일 카운터와 10분 requestId 실행 상태를 확보하며, 처리 중·성공 중복과 한도 초과는 기존 공통 409·429 계약으로 변환한다. 성공 시 requestId를 완료하고 AI·허용 목록 처리 실패 시 처리 상태를 해제하되 이미 확보한 호출량은 복구하지 않는다. Controller·Service·Region 공개 계약 테스트와 기존 MySQL 동시성 테스트를 포함해 실제 OpenAI 없이 전체 116개 테스트가 통과했다. 메뉴 분석 HTTP API와 구조 오류 재시도 한도 연결은 A1-05 범위다.

A1-05에서 인증된 `POST /api/ai/menus/analyze`와 필수 UUID `Idempotency-Key` 계약을 구현했다. 최초 AI 호출 전에 `AI_MENU_ANALYSIS`의 사용자별 3회/분·15회/일 카운터와 10분 requestId 실행 상태를 확보한다. `AI_RESPONSE_INVALID`만 최대 한 번 재시도하고 두 번째 실제 AI 호출 직전에 사용량 1회를 추가 확보하므로, 잔여 한도가 없으면 두 번째 AI 호출 없이 429와 재시도 정보를 반환한다. 두 번째 구조 오류는 안전한 503 `AI_RESPONSE_INVALID`로 끝나며 요청·응답 원문이나 기존 작성 상태를 서버에 저장하지 않아 클라이언트가 기존 입력을 유지하고 직접 메뉴 입력으로 전환할 수 있다. 성공·중복·초기 및 재시도 한도·재시도 성공·두 번째 구조 오류와 Controller validation·오류 계약을 실제 OpenAI 없이 검증했고 전체 125개 테스트가 통과했다. AI 단계 전체 DoD 점검은 A1-06 범위다.

A1-06 회귀 점검에서 AI 기능·보안·외부 호출 격리와 전체 테스트는 통과했지만 메뉴 AI의 정확한 분·일 한도 경계가 자동 테스트로 고정되지 않은 점을 발견했다. 별도 A1-06A에서 지역 AI 2회/분·10회/일과 메뉴 AI 3회/분·15회/일의 마지막 허용 호출과 다음 호출 차단을 고정 시계와 MySQL 공유 저장소로 검증했다. 기존 Asia/Seoul 자정 전환 검증과 함께 실제 OpenAI 호출 없이 전체 127개 테스트, `git diff --check`를 통과해 A1 단계를 완료했다.

A1-07에서 메뉴 분석 입력 경계를 보완했다. 응답의 `name`, `searchQuery`, `reason`, `targetClientPlaceId` 형식과 request의 기존 공백 거절 계약은 유지하고, regionId는 trim하지 않은 원문으로 최종 선택 가능한 국내 지역과 정확히 일치하는지 검증한다. 관광지 맥락은 0~35개이며 clientPlaceId는 보정하지 않는 공백 없는 1~100자이자 요청 안에서 중복될 수 없고, displayName은 trim 후 1~50자다. 이 입력 검증은 requestId 선점·호출량 차감·AI 호출보다 먼저 수행되어 위반 시 400 `VALIDATION_FAILED`로 끝난다. 식별자 비보정 회귀 기대값을 포함해 루트 `test.ps1`의 Testcontainers MySQL 기반 전체 132개 테스트가 실패·오류·skip 없이 통과했다.

U1-07에서 회원가입 저장 중 발생한 DB 무결성 오류의 의미를 구분했다. MySQL 중복 키 오류 1062만 409 `EMAIL_ALREADY_EXISTS`로 변환하고, 다른 무결성 오류는 이메일 중복으로 오인하지 않고 원래 예외를 유지해 공통 500 처리 대상으로 남긴다. DB 오류 문구나 사용자 이메일을 검사·노출하지 않으며, 중복 키 경쟁과 다른 제약 오류 단위 테스트를 포함해 루트 `test.ps1`의 전체 133개 테스트가 실패·오류·skip 없이 통과했다.

U1-08에서 호출 한도 초과의 `retryAfterSeconds`를 남은 시간의 올림값으로 계산하도록 보완했다. 정수 초 경계는 기존 값을 유지하고 소수 초가 남으면 1초를 더하며, 만료 경계에서도 최소 1초를 반환한다. 59.9초 잔여 시간이 60초로 반환되는 회귀 테스트를 포함해 루트 `test.ps1`의 전체 134개 테스트가 실패·오류·skip 없이 통과했다.

F0-06에서 API 명세에 없는 개발 확인용 `GET /hello`와 `HelloController`를 제거했다. 해당 경로는 운영 애플리케이션에서 공통 404 `RESOURCE_NOT_FOUND`의 여섯 필드 오류 계약으로 처리되며, 실제 Spring Security·MVC·MySQL 구성을 사용하는 통합 회귀 테스트로 고정했다. 루트 `test.ps1`의 전체 135개 테스트가 실패·오류·skip 없이 통과했다. JWT TTL과 브라우저 판정 정렬, AI 출력 문자열 정규화는 각각 미실행 후속 작업 `W1-01C`, `A1-08`로 로드맵에만 등록했다.

P1-01에서 장소 역할을 관광지·숙소·음식점으로 분리하고 역할별 반경을 관광지 20km, 숙소 5→10km, 음식점 1→3→5km의 불변 확장 순서로 구현했다. 카카오 카테고리는 요청 범위에서만 일반·자연 90분, 박물관·전시 120분, 체험 180분, 등산 240분, 테마파크 360분으로 변환하며, 불명확한 관광지는 90분, 음식점은 60분, 숙소는 관광 체류시간 없음으로 처리한다. 내부 체류 분류는 private으로 숨기고 숫자만 반환하며 관광지 조정값은 30~480분의 10분 배수만 허용한다. 역할별 반경, 분류 우선순위·기본값, 호텔 제외, 조정 경계와 유형 미노출 테스트를 포함해 루트 `test.ps1` 전체 232개 테스트가 실패·오류·건너뜀 없이 통과했다. 실제 Kakao HTTP Client와 공개 검색 API, 사용자 표시 이름 입력은 후속 P1 작업 범위다.

## 8. 완료 해석

- Accepted ADR은 구현 완료가 아니다.
- 목표 DB·API 문서가 존재해도 migration이나 endpoint가 구현됐다는 뜻은 아니다.
- 하네스 파일과 Fake Client는 실제 도메인 기능 또는 제공자 정책 검증을 대신하지 않는다.
- 카카오 임시 사용 조건 확인만으로 좌표 기반 제작 흐름을 완료로 표시하지 않는다. 구현과 테스트가 필요하다.
- 구현 완료는 관련 테스트, 전체 테스트와 `docs/10-definition-of-done.md`를 확인한 뒤에만 기록한다.
- 운영 가능은 구현 완료에 더해 비밀값, 로그, 호출 한도, health, smoke와 확정된 카카오 데이터 수명 계약을 충족해야 한다.
