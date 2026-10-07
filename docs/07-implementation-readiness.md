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

## 2. 현재 코드 기준선

| 영역 | 상태 | 확인 근거 |
|---|---|---|
| Spring Boot | 기본 골격 실행 가능 | `TravelApplication` 기동과 명세 밖 개발 endpoint의 공통 404 처리 확인 |
| Java·Gradle | 현재 골격 실행 가능 | JDK 21.0.12.1, Gradle 9.7.1, Spring Boot 4.1.1로 빌드 확인 |
| Web·Validation | dependency 존재 | webmvc·validation starter와 테스트 starter의 runtime·testRuntime classpath 확인 |
| JPA·MySQL·Flyway | 기반 구현·검증 완료 | 승인 dependency와 profile 설정을 적용하고 MySQL 8.4에서 Flyway 실행 후 `ddl-auto: validate` 통과 |
| Docker MySQL | 테스트 연결 검증 | Docker Engine과 Testcontainers MySQL 8.4 연결 성공, local Compose의 수동 연결·배포 검증은 남음 |
| 인증·보안 | U1 범위 구현·검증 완료 | 회원가입·JWT 로그인, 공개 API 경계, MySQL 호출 카운터와 requestId 실행 상태의 회귀 점검을 완료했다. TravelPlan 조회·수정·삭제 소유권은 T1-07~08에서 검증했으며 회원 탈퇴는 후속 작업이다. |
| 지역 | G1 단계 구현·검증 완료 | 출처가 확인된 `regions.json` 246개와 최종 지역 161개의 공식 경계 기반 `searchBounds`, 시작 검증, 메모리 Catalog와 결정적 직접 검색 API 및 AI용 최종 선택 가능 지역 공개 계약을 전체 테스트로 검증했다. 완료 일정의 지역 이름 snapshot 저장·조회는 T1-05~07에서 검증했다. |
| AI | A1 단계·A1-08 구현·자동 검증 완료 | DTO·Service·Fake와 prod·smoke 실제 Responses API Client에 인증된 지역·메뉴 HTTP API, 기능별 사용자 한도와 requestId 처리를 연결하고 AI 단계 전체 DoD와 한도 경계를 검증했다. 메뉴 출력의 앞뒤 공백 제거와 중복·공백 오류도 검증했다. 지역 추천 브라우저 연결은 W1-01B, 메뉴 분석 브라우저 연결은 W1-02A에서 검증했다. |
| Place | P1 범위·P1-07B~D·S1-05·S1-06A 서버 범위 구현·검증 완료 | Kakao Local Client와 관광지·여행 경계·숙소·음식점 검색을 연결했다. 음식점 후보는 1·3·5km 또는 현재 지도 영역에서 찾고 주소 검증 뒤 `RESTAURANT` token을 발급한다. 숙소는 점수 없이 직접 선택한다. 관광지·경계·숙소 화면은 W1-02, 음식점 화면은 W1-03A에서 가짜 API로 검증했다. 완료 생성의 좌표 수명 검증은 T1 후속 작업이다. |
| Route | R1·R2·R2-09A 구현·검증 완료 | 순수 경로 알고리즘, 자동차·대중교통 Client, 최초 인접 구간과 재시도 직전 쿼터 확보, 미호출 예약 반환, 확보 실패 전체 fallback과 USER·SERVICE 단독·동시 차단 범위의 비식별 관측 전달을 구현했다. 실제 호출 수와 최종 차감량, 외부 호출 전 트랜잭션 종료, warning 비노출 계약을 R2-08에서 회귀 검증했다. R2-09에서 첫 경로 없음 뒤 호출 중단과 미호출 예약 반환을, R2-09A에서 큰 양수 이동시간 변환 실패의 조기 종료와 미호출 예약 반환을 검증했다. |
| Recommendation | S1-06·S1-06A 서버 범위 구현·검증 완료 | 재계산된 식사 시각과 직전·직후 장소의 가용 시각으로 후보의 시간 적합성을 검사하고 Haversine 추가 이동거리와 결정적 동률 규칙으로 정렬한다. 음식점 검색 HTTP와 연결했으며 빈 후보와 제공자 장애를 구분한다. 지도·목록 연동과 사용자 직접 선택은 W1-03A의 가짜 API 브라우저 범위에서 검증했다. |
| TravelPlan | S1-01~04·S1-06A·T1-01~09·T1-03A·T1-06C·T1-07A·T1-08A 구현·자동 검증, T1-10 점검 완료 | estimate는 DB와 실제 Route Client 없이 시간표를 계산한다. 완료 생성은 확정한 날짜·순서를 다시 검증하고 인접 경로 예상시간·식사 슬롯·종료 시각을 재계산한 뒤 Aggregate와 requestId 성공 상태를 한 트랜잭션으로 저장한다. 계산 후보의 0분 MOVE는 저장·생성·상세 응답에서 생략하고 Item 순서를 다시 매긴다. STAY Item은 저장하지 않는다. 경로 없음 422는 종료형 결과에서 최종 후보의 `date + moveOrder + travelMode`만 세부 정보로 반환하며 저장을 시작하지 않는다. 목록·상세는 인증 사용자 소유 일정의 저장 DTO만 반환하고 생성·상세는 저장된 숙소를 nullable `hotel`로 제공한다. 완료 후 제목·장소 표시 이름·메모만 부분 수정하며 PATCH·DELETE는 같은 일정의 잠금을 공유하고 PATCH 응답을 수정 트랜잭션 안에서 구성한다. 삭제는 공유 토큰을 포함한 Aggregate 전체를 한 트랜잭션에서 제거한다. 공유 토큰은 Aggregate 잠금 아래 재발급하고 원문 문자열의 해시만 저장하며, 공개 조회는 저장된 일정의 읽기 전용 DTO를 반환한다. T1-10에서 완료 일정 금지 열·저장값·응답, V5→V6 승격과 rollback을 점검했다. 실제 제공자 smoke와 공유 화면은 후속 작업이다. |
| DB migration | V1~V6 빈 DB·V5→V6 승격 검증 완료 | V1 `users`, V2 `api_usage_counters`, V3 `request_executions`, V4 호출 카운터 기능값 제약, V5 완료 일정 Aggregate, V6 STAY Item 금지를 Testcontainers MySQL 8.4의 빈 DB에 적용하고 Hibernate validate·DB 제약 테스트를 통과했다. 별도 Testcontainers MySQL에서 기존 V5 일정의 STAY 0건을 확인하고 V6를 적용해 데이터 보존과 STAY 금지를 검증했다. 로컬 영속 DB 적용과 백업·복구는 Q1-05에서 확인한다. |
| 자동 테스트 | T1-10까지 510개 통과 | Docker/Testcontainers 기반 루트 `test.ps1`에서 완료 일정 저장 금지 열·값·응답, V5→V6 승격, rollback과 기존 회귀를 검증했다. 실패·오류·건너뜀은 0개다. |
| 화면 | W1-00·W1-01A~C·W1-02·W1-02A·W1-03·W1-03A·W1-03B 범위 구현·가짜 API 브라우저 검증 완료 | 공통 app shell·6개 view·8단계 Workspace에 인증·지역·여행 조건·관광지·여행 경계·숙소 검색과 메모리 선택, 메뉴 분석·확정, 날짜별 활동 시간·관광지 직접 배치·추정 일정과 식사 슬롯별 음식점 목록·지도 선택을 연결했다. W1-03B에서 경로 없음 오류의 정확한 구간 강조와 날짜 단위 fallback을 검증했다. 카카오 지도 SDK는 브라우저용 키가 있을 때 지연 로드하고 키가 없으면 목록 선택을 유지한다. 실제 완료 생성 요청·완료 화면과 실제 OpenAI·카카오 지도·장소 smoke는 후속 검증이다. |

## 3. 문서·하네스 정렬 결과

D0 문서·하네스 정렬과 K0-02A 정책 확인을 완료했다. 정책 확인은 좌표 기반 기능의 구현·운영 검증을 대신하지 않는다.

## 4. 제품 결정 준비도

| 범위 | 현재 상태 | 남은 일 |
|---|---|---|
| 인증·지역·AI·장소·경로 기반 | 해당 단계 구현·자동 검증 완료 | 실제 제공자와 배포 환경의 smoke 검증 |
| 일정 추정·음식점 추천 | S1 서버 기능과 W1-03A까지 가짜 API 브라우저 흐름 검증 | 실제 카카오 지도 SDK·Local API smoke |
| 완료 일정·공유 | T1-01~09 완료 생성·조회·제한 편집·삭제·공유 API 및 T1-10 저장·DB 점검과 T1-03A STAY 금지·T1-06C 0분 MOVE 회귀·T1-07A 숙소 응답·T1-08A 동시 수정·삭제 보호·R2-09A 경로 응답 보완의 자동 테스트 통과 | W1-04 완료 화면의 좌표 폐기, Q1-05 로컬 DB 승격·복구, Q1-06 실제 제공자 smoke |
| 운영 배포 | 구현 전 | Q1 운영·보안·배포 완료 기준 검증 |

## 5. 구현 전 확정할 세부 계약

현재 T1 완료 저장·공유에 남은 제품 정책 결정은 없다. 식사 연결·계정 삭제·공유 토큰 계약은 ADR-043, DB·HTTP 계약은 각각 `docs/03-database.md`와 `docs/04-api-spec.md`를 따른다. 결정 완료는 Entity·migration·API 구현 완료를 뜻하지 않는다.

## 6. 문서 정렬 후 진행 가능한 범위

작업 순서와 선행 조건은 `docs/11-command-roadmap.md`를 따른다. T1-10 저장·DB 자동 점검을 완료했다. 다음 작업은 `docs/11-command-roadmap.md` 16절을 따른다.

## 7. 남은 검증

최신 구현과 자동 테스트 근거는 2절, 다음 작업 순서는 `docs/11-command-roadmap.md` 16절을 따른다. 단계별 과거 테스트 수와 완료 작업 이력은 이 문서에 누적하지 않는다.

- 로컬 영속 V5 DB에 V6를 적용하기 전 STAY 행 수를 확인하고, 백업·복구와 배포 절차를 Q1-05에서 검증한다(`docs/09-operations.md` 11절).
- 실제 카카오·OpenAI 제공자 연결과 좌표 수명, 브라우저 실제 연동 및 운영 배포는 각 단계에서 별도로 검증한다. 현재 자동 테스트 통과를 해당 검증의 완료로 간주하지 않는다.
- `W1-04` 이후 화면 작업이 남아 있다.

## 8. 완료 해석

- 설계·정책 확인과 Fake 기반 자동 테스트는 실제 제공자 smoke 또는 운영 가능 판정을 대신하지 않는다.
- 기능 완료는 관련 테스트, 전체 테스트와 `docs/10-definition-of-done.md`를 확인한 뒤 기록한다.
