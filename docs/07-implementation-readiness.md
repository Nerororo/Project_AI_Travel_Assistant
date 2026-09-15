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

## 2. 코드 기준선 (2026-09-15, F0-01 감사 완료)

| 영역 | 상태 | 확인 근거 |
|---|---|---|
| Spring Boot | 기본 골격 실행 가능 | `TravelApplication` 기동과 `GET /hello` HTTP 200 확인 |
| Java·Gradle | 현재 골격 실행 가능 | JDK 21.0.12.1, Gradle 9.7.1, Spring Boot 4.1.1로 빌드 확인 |
| Web·Validation | dependency 존재 | webmvc·validation starter와 테스트 starter의 runtime·testRuntime classpath 확인 |
| JPA·MySQL·Flyway | 기반 구현·검증 완료 | 승인 dependency와 profile 설정을 적용하고 MySQL 8.4에서 Flyway 실행 후 `ddl-auto: validate` 통과 |
| Docker MySQL | 테스트 연결 검증 | Docker Engine과 Testcontainers MySQL 8.4 연결 성공, local Compose의 수동 연결·배포 검증은 남음 |
| 인증·보안 | 부분 구현 | User Entity·Repository와 V1 migration 검증 완료, 회원가입·Security·JWT는 구현 전 |
| 지역 | 하네스만 존재 | `region/AGENTS.md`만 있고 Java 코드·`regions.json` 없음 |
| AI | 하네스만 존재 | Client·Service·DTO 코드 없음 |
| Place | 하네스만 존재 | Client·Service·DTO 코드 없음 |
| Route | 하네스만 존재 | 알고리즘·Client·Service 코드 없음 |
| Recommendation | 하네스만 존재 | Service·정책 코드 없음 |
| TravelPlan | 하네스만 존재 | Entity·Repository·Service·DTO 코드 없음 |
| DB migration | User schema 구현·검증 | V1 `users` migration을 MySQL 8.4에 적용하고 Hibernate validate 통과, 후속 업무 schema는 구현 전 |
| 자동 테스트 | User DB 기반 통합 검증 | 전체 24개 통과, User Repository 저장·조회와 이메일 대소문자 UNIQUE·비밀번호 hash 컬럼만 존재함을 MySQL 8.4에서 검증 |
| 화면 | 정적 시안 | `static/Routy` HTML·CSS·JavaScript·미리보기 테스트, 새 API 미연동 |

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
| 국내 범위 | 확정 | 구현 전 | 서울·광역시·세종은 자체 선택, 도·특별자치도는 하위 시·군 선택, 광역자치단체의 구·군은 검색 필터, 읍·면·동·해외 제외 |
| 지역 직접 검색·AI 추천 | 확정 | 구현 전 | 같은 `regions.json` 허용 목록 사용 |
| 이동수단 | 확정 | 구현 전 | 일정당 CAR 또는 PUBLIC_TRANSIT 하나 |
| 순수 경로 | 확정 | 구현 전 | Haversine, Nearest Neighbor, 2-opt |
| 실제 경로 호출 시점 | 확정 | 구현 전 | 최종 후보의 인접 구간만 조회 |
| 이동 시간 | 확정 | 구현 전 | 제공자 예상 초를 10분 단위로 올림 |
| 체류 시간 | 확정 | 구현 전 | 유형 미노출, 30~480분의 10분 단위 |
| 사용자 장소 이름 | 확정 | 구현 전 | 빈 입력에서 직접 작성, trim 후 1~50자 |
| 시간 초과 | 확정 | 구현 전 | 저장 차단, 자동 삭제·체류 축소 없음 |
| 완료 후 편집·조회·공유 | 확정 | 구현 전 | 제한 텍스트 편집, 외부 호출·지도 없는 조회 |
| 인증·회원 탈퇴 | 확정 | 구현 전 | ADR-037: 비밀번호 8~64자·UTF-8 72바이트 이하, 1시간 access JWT만 사용, 공개 API 3개, 탈퇴 시 소유 데이터 삭제 |
| 호출 한도·저장소 | 확정 | 구현 전 | MySQL 공유 카운터·requestId 상태, 처리 중·성공 중복 409, 경로 쿼터 사전 확보 |
| 이동시간 출처 | 확정 | 구현 전 | 숫자만 저장, 생성 warning 비영속, 집계 metric |
| 카카오 좌표 활용 계약 | 정책 확인 완료 | 구현 전 | 2026-09-11 DevTalk 답변과 ADR-028 |
| 운영 배포 | 차단 | 구현 전 | 기능·테스트·운영 검증 필요 |

카카오 답변 반영은 완료됐다. 이후 모든 좌표 기반 구현은 ADR-028의 일시 사용·즉시 폐기 조건을 따라야 한다.

## 5. 구현 전 확정할 세부 계약

| 항목 | 로드맵 시점 | 기록 위치 |
|---|---|---|
| 테스트 DB 방식과 JPA·MySQL·Flyway dependency | 결정 완료 | MySQL 8.4 Testcontainers, Spring Boot 관리 버전, `docs/08` 3절과 `docs/09` 11절 |
| JWT 만료·재발급·로그아웃, User 삭제 | 결정 완료 | ADR-037, `docs/03` User 삭제, `docs/04` 1~2절, `docs/09` 3·10절 |
| 지역 공공데이터 출처·기준일·생성 절차 | G1-01 | 데이터 ADR·운영 |
| CAR·PUBLIC_TRANSIT 초기 추정 계수 | R1-01 | ADR·테스트 |
| OpenAI 모델·전체 예산 | A1-03 전 | 운영·AI 계약 |
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

현재 실제 기능은 구현 전이다. 활성 구현 작업과 다음 실행 순서는 `docs/11-command-roadmap.md`를 따른다.

F0-01에 필요한 코드 기준선은 문서 작업 중 읽기 전용으로 확인했지만 정식 작업 완료로 표시하지 않는다. 사용자가 개발 시작을 별도로 요청할 때만 F0 이후 작업을 진행한다.

U1-01에서 비밀번호·JWT·공개 endpoint·User 삭제 계약을 ADR-037로 확정했다. 이는 설계 완료이며 User Entity, migration, 회원가입과 Spring Security·JWT 구현은 각각 U1-02~04에서 검증해야 한다.

U1-02에서 User Entity·Repository와 V1 `users` migration을 구현했다. MySQL 8.4에서 production migration 적용, Hibernate `ddl-auto: validate`, Repository 저장·조회, 이메일 대소문자 UNIQUE와 `password_hash`만 존재하는 schema를 통합 테스트로 검증했다. 회원가입 시 이메일 정규화·해시 생성·중복 오류 변환은 U1-03, 인증과 JWT는 U1-04 범위다.

## 8. 완료 해석

- Accepted ADR은 구현 완료가 아니다.
- 목표 DB·API 문서가 존재해도 migration이나 endpoint가 구현됐다는 뜻은 아니다.
- 하네스 파일과 Fake Client는 실제 도메인 기능 또는 제공자 정책 검증을 대신하지 않는다.
- 카카오 임시 사용 조건 확인만으로 좌표 기반 제작 흐름을 완료로 표시하지 않는다. 구현과 테스트가 필요하다.
- 구현 완료는 관련 테스트, 전체 테스트와 `docs/10-definition-of-done.md`를 확인한 뒤에만 기록한다.
- 운영 가능은 구현 완료에 더해 비밀값, 로그, 호출 한도, health, smoke와 확정된 카카오 데이터 수명 계약을 충족해야 한다.
