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
| 인증·보안 | U1 범위 구현·검증 완료 | 회원가입·JWT 로그인, 공개 API 경계, MySQL 호출 카운터와 requestId 실행 상태의 회귀 점검 완료, 회원 탈퇴·TravelPlan 소유권은 후속 작업 |
| 지역 | G1 단계 구현·검증 완료 | 출처가 확인된 `regions.json` 246개와 최종 지역 161개의 공식 경계 기반 `searchBounds`, 시작 검증, 메모리 Catalog와 결정적 직접 검색 API 및 AI용 최종 선택 가능 지역 공개 계약을 전체 테스트로 검증했으며 완료 일정 snapshot은 후속 T1 책임 |
| AI | A1 단계 구현·검증 완료 | DTO·Service·Fake와 prod·smoke 실제 Responses API Client에 인증된 지역·메뉴 HTTP API, 기능별 사용자 한도와 requestId 처리를 연결하고 AI 단계 전체 DoD와 한도 경계를 검증했다. 지역 추천 브라우저 연결은 W1-01B, 메뉴 분석 브라우저 연결은 W1-02A에서 검증했다. |
| Place | P1 범위·P1-07B~D·S1-05·S1-06A 서버 범위 구현·검증 완료 | Kakao Local Client와 관광지·여행 경계·숙소·음식점 검색을 연결했다. 음식점 후보는 1·3·5km 또는 현재 지도 영역에서 찾고 주소 검증 뒤 `RESTAURANT` token을 발급한다. 숙소는 점수 없이 직접 선택한다. 관광지·경계·숙소 화면은 W1-02, 음식점 화면은 W1-03A에서 가짜 API로 검증했다. 완료 생성의 좌표 수명 검증은 T1 후속 작업이다. |
| Route | R1·R2·R2-09 구현·검증 완료 | 순수 경로 알고리즘, 자동차·대중교통 Client, 최초 인접 구간과 재시도 직전 쿼터 확보, 미호출 예약 반환, 확보 실패 전체 fallback과 USER·SERVICE 단독·동시 차단 범위의 비식별 관측 전달을 구현했다. 실제 호출 수와 최종 차감량, 외부 호출 전 트랜잭션 종료, warning 비노출 계약을 R2-08에서 회귀 검증했다. R2-09에서 첫 경로 없음 뒤 호출 중단과 미호출 예약 반환을 검증했다. |
| Recommendation | S1-06·S1-06A 서버 범위 구현·검증 완료 | 재계산된 식사 시각과 직전·직후 장소의 가용 시각으로 후보의 시간 적합성을 검사하고 Haversine 추가 이동거리와 결정적 동률 규칙으로 정렬한다. 음식점 검색 HTTP와 연결했으며 빈 후보와 제공자 장애를 구분한다. 지도·목록 연동과 사용자 직접 선택은 W1-03A의 가짜 API 브라우저 범위에서 검증했다. |
| TravelPlan | S1-01~04·S1-06A·T1-01~08 구현·자동 검증 완료 | estimate는 DB와 실제 Route Client 없이 시간표를 계산한다. 완료 생성은 확정한 날짜·순서를 다시 검증하고 인접 경로 예상시간·식사 슬롯·종료 시각을 재계산한 뒤 Aggregate와 requestId 성공 상태를 한 트랜잭션으로 저장한다. 경로 없음 422는 종료형 결과에서 최종 후보의 `date + moveOrder + travelMode`만 세부 정보로 반환하며 저장을 시작하지 않는다. 목록·상세는 인증 사용자 소유 일정의 저장 DTO만 반환한다. 완료 후 제목·장소 표시 이름·메모만 부분 수정하며, 삭제는 공유 토큰을 포함한 Aggregate 전체를 한 트랜잭션에서 제거한다. 실제 제공자 smoke와 공유는 후속 작업이다. |
| DB migration | User·호출 카운터·requestId schema 구현·검증 | V1 `users`, V2 `api_usage_counters`, V3 `request_executions`, V4 호출 카운터 기능값 제약을 MySQL 8.4에 적용하고 Hibernate validate 통과 |
| 자동 테스트 | T1-08까지 496개 통과 | Docker/Testcontainers 기반 루트 `test.ps1`에서 완료 일정 부분 수정의 필드 제한·소유권과 Aggregate 삭제·rollback 및 기존 회귀를 검증했다. 실패·오류·건너뜀은 0개다. |
| 화면 | W1-00·W1-01A~C·W1-02·W1-02A·W1-03·W1-03A 범위 구현·가짜 API 브라우저 검증 완료 | 공통 app shell·6개 view·8단계 Workspace에 인증·지역·여행 조건·관광지·여행 경계·숙소 검색과 메모리 선택, 메뉴 분석·확정, 날짜별 활동 시간·관광지 직접 배치·추정 일정과 식사 슬롯별 음식점 목록·지도 선택을 연결했다. 카카오 지도 SDK는 브라우저용 키가 있을 때 지연 로드하고 키가 없으면 목록 선택을 유지한다. 완료 화면과 실제 OpenAI·카카오 지도·장소 smoke는 후속 검증이다. |

## 3. 문서·하네스 정렬 결과

D0 문서·하네스 정렬과 K0-02A 정책 확인을 완료했다. 정책 확인은 좌표 기반 기능의 구현·운영 검증을 대신하지 않는다.

## 4. 제품 결정 준비도

| 범위 | 현재 상태 | 남은 일 |
|---|---|---|
| 인증·지역·AI·장소·경로 기반 | 해당 단계 구현·자동 검증 완료 | 실제 제공자와 배포 환경의 smoke 검증 |
| 일정 추정·음식점 추천 | S1 서버 기능과 W1-03A까지 가짜 API 브라우저 흐름 검증 | 실제 카카오 지도 SDK·Local API smoke |
| 완료 일정·공유 | T1-01~08 완료 생성·조회·제한 편집·삭제 API의 기존 자동 테스트 통과 | T1-07A 숙소 응답, T1-08A 동시 수정·삭제 보호, R2-09A 경로 응답 보완 뒤 T1-09~10 공유·저장 금지 점검 |
| 운영 배포 | 구현 전 | Q1 운영·보안·배포 완료 기준 검증 |

## 5. 구현 전 확정할 세부 계약

현재 T1 완료 저장·공유에 남은 제품 정책 결정은 없다. 식사 연결·계정 삭제·공유 토큰 계약은 ADR-043, DB·HTTP 계약은 각각 `docs/03-database.md`와 `docs/04-api-spec.md`를 따른다. 결정 완료는 Entity·migration·API 구현 완료를 뜻하지 않는다.

## 6. 문서 정렬 후 진행 가능한 범위

작업 순서와 선행 조건은 `docs/11-command-roadmap.md`를 따른다. 다음 백엔드 작업은 `T1-07A`이며, 이어서 `T1-08A` → `R2-09A` → `T1-09`를 진행한다.

## 7. 현재 작업 상태

`T1-01`에서 미선택 MEAL의 null 장소 참조, 선택 식당만 PlanPlace에 연결하는 규칙, User·일정 삭제와 공유 토큰 해시·만료 정책을 ADR-043 및 DB·API 문서에 확정했다. 문서 전용 작업으로 Entity·migration·완료·공유 API는 구현하지 않았다. 당시 루트 `test.ps1` 전체 456개와 `git diff --check`가 통과했다. 실제 경로 시간으로 재검증한 완료 저장과 공유 기능은 후속 T1 작업이다. 별도 보완 작업 `A1-08`은 미실행 상태다.

`T1-02`에서 Item의 일정 소속을 검증하는 복합 FK, Aggregate의 UNIQUE·CHECK·삭제 경계와 V5 migration의 물리 계약을 `docs/03-database.md`에 확정했다. 루트 `test.ps1`과 `git diff --check`가 통과했으며 Gradle `test`는 `UP-TO-DATE`였다. Entity·migration·DB 적용 테스트는 `T1-03` 범위로 남아 있다.

`T1-03`에서 여섯 Entity와 Repository, V5 migration을 구현했다. Testcontainers MySQL의 빈 DB에 Flyway V5를 적용하고 Hibernate schema validate, 저장·복원, UNIQUE·CHECK·복합 FK 제약을 검증했다. 완료 계산·저장 Service와 API는 후속 T1 작업이다.

`T1-04`에서 확정한 관광지 날짜·순서를 유지하고 선택한 음식점만 최종 경유 지점에 넣어 Route 공개 Service의 구간별 예상 이동시간으로 시간표를 다시 계산하는 Service를 구현했다. 긴 체류 안의 선택 음식점 왕복과 식사 시간창·종료 시각을 검증하고 정상 경로 없음·제공자 계약 오류를 구분하며 warning은 요청 범위 결과에만 둔다. 집중 테스트 21개와 루트 `test.ps1` 전체 471개 테스트, `git diff --check`가 통과했다. DB 저장·HTTP 완료 생성 연결과 실제 제공자 smoke는 아직 수행하지 않았으며 각각 T1-05·T1-06과 별도 운영 검증 범위다.

`T1-05`에서 좌표·토큰·warning을 받지 않는 저장 명령과 Aggregate 저장 Service를 구현했다. 계산을 마친 결과의 장소 snapshot, 날짜, 항목, 확정 메뉴만 짧은 DB 트랜잭션으로 저장하며 0분 MOVE는 DB의 양수 이동시간 제약에 맞춰 생략한다. Testcontainers MySQL로 전체 저장과 중간 Item 실패 시 TravelPlan·Day·Item·PlanPlace·FoodPreference rollback을 검증했고 루트 `test.ps1` 전체 테스트와 `git diff --check`가 통과했다. 완료 생성 HTTP 연결과 실제 제공자 smoke는 각각 T1-06과 별도 운영 검증 범위다.

`T1-06`에서 `POST /api/travel-plans`를 연결했다. 사용자 확정 배치와 선택 token·식사를 검증하고 서버가 실제 경로 예상시간으로 다시 계산한 뒤, 완료 Aggregate와 requestId 성공 상태를 같은 짧은 트랜잭션에서 저장한다. 201 Location·저장 데이터 응답, 용량 초과 422, 제공자 장애 503, 중복 requestId 409와 생성 응답 전용 warning을 집중 테스트로 확인했다. 후속 Testcontainers 검증에서 JWT 인증 HTTP 401·201·409, 실제 MySQL 저장·중복 차단·용량 초과 후 미저장과 재시도·음식점 선택/미선택·대중교통 숙소 흐름도 통과했다. `local`·`test`는 외부 경로를 호출하지 않는 Client로 전체 Haversine fallback을 사용한다. 루트 `test.ps1` 484개 테스트가 실패·오류·건너뜀 없이 통과했다. `ROUTE_NOT_FOUND` 실패 구간 세부 DTO는 T1-06A, 실제 카카오 제공자 smoke는 별도 운영 검증 범위다.

`T1-06A`에서 완료 생성의 `ROUTE_NOT_FOUND`에 실패한 날짜, 최종 후보에서 1부터 세는 `MOVE` 순서, 요청 이동수단만 담는 세부 DTO를 연결했다. 첫·중간·마지막 이동, 선택 식사의 왕복, 다음 날 숙소 출발과 같은 숙소 반복 방문을 검증했다. 422 응답에는 장소·좌표·제공자 정보가 없고, 실패 시 저장 트랜잭션을 시작하지 않으며 requestId 처리 상태를 해제한다. 집중 테스트와 루트 `test.ps1` 전체 490개 테스트가 실패·오류·건너뜀 없이 통과했고 `git diff --check`도 통과했다. 브라우저의 작성 상태 유지·구간 강조는 W1-03B, 실제 카카오 제공자 smoke는 별도 운영 검증 범위다.

`R2-09`와 `T1-06B`에서 여러 구간 중 첫 정상 경로 없음 뒤의 외부 호출을 중단하고 미호출 구간의 예약 호출량을 반환하도록 보완했다. 완료 계산은 이 종료형 결과의 실패 구간을 길이 검사보다 먼저 확인해 `date + moveOrder + travelMode`가 있는 422로 변환한다. 뒤 구간의 기술 장애가 앞선 경로 없음을 fallback으로 덮지 못하며, 경로 없음이 없는 비정상적으로 짧은 결과는 계속 거절한다. 실제 경로 서비스와 완료 계산을 연결한 회귀 테스트를 포함해 루트 `test.ps1` 전체 493개가 실패·오류·건너뜀 없이 통과했다. 실제 제공자 smoke는 별도 운영 검증 범위다.

`T1-07`에서 `GET /api/travel-plans`와 `GET /api/travel-plans/{travelPlanId}`를 연결했다. 목록은 인증 사용자 일정만 생성 시각 내림차순으로 반환하고, 상세는 소유권 확인 뒤 저장된 지역 snapshot·날짜·시간표·사용자 이름·메모·카카오 링크만 조립한다. 타인 일정 403과 없는 일정 404를 구분하며 생성 시 warning은 조회 응답에 포함하지 않는다. 조회 Service는 외부 Client에 의존하지 않는다. JWT와 MySQL을 거친 통합 테스트를 포함해 루트 `test.ps1` 전체 494개가 실패·오류·건너뜀 없이 통과했고 `git diff --check`도 통과했다. 편집·삭제·공유와 실제 제공자 smoke는 후속 작업이다.

`T1-08`에서 `PATCH /api/travel-plans/{travelPlanId}`는 제목·소유 일정의 장소 표시 이름·메모만 부분 수정하고, 날짜·순서·시각·체류시간·이동수단·장소 ID·URL 변경 필드는 저장 전 400으로 거절한다. `DELETE`는 소유권 확인 뒤 공유 토큰·Item·Day·PlanPlace·FoodPreference·TravelPlan을 한 트랜잭션에서 삭제하고 204를 반환한다. JWT·MySQL 통합 테스트에서 타인 일정 403, 없는 일정 404, 금지 필드 거절, 삭제와 강제 실패 시 rollback을 검증했다. 외부 Client·경로 재계산은 사용하지 않는다. 루트 `test.ps1` 전체 496개가 실패·오류·건너뜀 없이 통과했고 `git diff --check`도 통과했다. 공유 발급·조회와 실제 제공자 smoke는 후속 작업이다.

후속 코드 검토에서 미해결 문제 세 가지를 확인했다. 여러 날 일정의 숙소 PlanPlace가 생성·상세 응답에 없어 편집용 `planPlaceId`를 얻을 수 있는 계약이 부족하다(`T1-07A`). 같은 일정에 대한 동시 PATCH·DELETE는 변경 유실 또는 수정 커밋 후 응답 조회의 404가 가능하다(`T1-08A`). 경로 제공자의 지나치게 큰 양수 이동시간이 변환 중 오류를 일으키면 미호출 예약 호출량 반환 경로를 벗어날 수 있다(`R2-09A`). 기존 496개 테스트 통과는 이 세 문제의 해결 증거가 아니며, 각 Task의 구현·검증은 아직 시작하지 않았다.

## 8. 완료 해석

- 설계·정책 확인과 Fake 기반 자동 테스트는 실제 제공자 smoke 또는 운영 가능 판정을 대신하지 않는다.
- 기능 완료는 관련 테스트, 전체 테스트와 `docs/10-definition-of-done.md`를 확인한 뒤 기록한다.
