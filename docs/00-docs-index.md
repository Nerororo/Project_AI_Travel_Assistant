# Routy 프로젝트 문서 안내

## 현재 기준

Routy는 서울특별시·광역시·세종특별자치시 또는 도·특별자치도 아래 시·군 하나를 최종 여행 지역으로 고르고, 자동차 또는 대중교통 일정 하나를 만드는 Spring Boot 백엔드 프로젝트다.

- Java 21, Spring Boot 4.1.1, Gradle
- 목표 DB: MySQL 8.4, Spring Data JPA, Flyway
- 현재 코드: Spring Boot 골격과 `GET /hello`
- AI: 허용 지역 후보와 음식 검색어 구조화
- 장소: 카카오 Local
- 경로: 카카오모빌리티 자동차, 카카오맵 대중교통
- 확인된 외부 조건: 2026-09-11 카카오 DevTalk 답변에 따라 작성 중 좌표의 일시 저장·참조와 즉시 폐기 구조 허용

목표 설계와 실제 구현 상태를 혼동하지 않는다. 현재 상태는 `07-implementation-readiness.md`를 따른다.

## 문서 구성

| 문서 | 단일 책임 |
|---|---|
| `01-requirements.md` | 제품 범위와 기능·비기능 요구사항 |
| `02-architecture.md` | 계층·도메인·외부 시스템 책임과 데이터 흐름 |
| `03-database.md` | 완료 일정 Aggregate와 영속 저장 계약 |
| `04-api-spec.md` | HTTP endpoint, DTO, 오류와 호출 한도 |
| `05-development-plan.md` | 큰 개발 방향과 학습 목표 |
| `06-decisions.md` | 설계 결정, 이유, 상태와 재검토 조건 |
| `07-implementation-readiness.md` | 코드 기준선, 준비·차단·구현 상태 |
| `08-test-strategy.md` | 테스트 수준과 도메인별 필수 검증 |
| `09-operations.md` | profile, 비밀값, 로그, 쿼터, 장애와 배포 |
| `10-definition-of-done.md` | 검증·운영 가능 여부의 완료 판정 |
| `11-command-roadmap.md` | 실제 작업 ID, 순서와 선행 조건 |
| `12-harness-boundaries.md` | 단계별 Allowed·Conditional·Forbidden Paths |

프로젝트 인계와 결정 과정은 루트 `REMAKE.md`, 현재 정적 화면의 상태와 연결 지점은 `src/main/resources/static/Routy/INTEGRATION.md`에 있다.

## 선택적 읽기

전체 문서를 관성적으로 읽지 않는다. 적용되는 `AGENTS.md`, 로드맵 작업 ID, 하네스의 해당 단계 행, 대상 파일과 직접 참조 코드부터 확인한다.

| 작업 | 필수 기준 | 조건부 기준 |
|---|---|---|
| 작업 선택 | `11-command-roadmap.md` 해당 ID, `12-harness-boundaries.md` 해당 단계 | 큰 방향은 `05-development-plan.md` |
| 요구사항·정책 | `01-requirements.md` 관련 절 | 책임 변경은 `02-architecture.md`, 결정 변경은 `06-decisions.md` |
| Controller·DTO | `04-api-spec.md` 해당 endpoint | 공통 오류·인증 관련 절 |
| Entity·Repository·migration | `03-database.md` 관련 절 | 공개 API가 바뀌면 `04-api-spec.md` |
| 알고리즘·추천 | `01-requirements.md`, `08-test-strategy.md` 관련 절 | 관련 ADR |
| AI·카카오 Client | `04-api-spec.md`, `09-operations.md` | 해당 도메인 `AGENTS.md`, 관련 ADR |
| 테스트 | `08-test-strategy.md` | 완료 판정은 `10-definition-of-done.md` |
| 설정·배포 | `09-operations.md` | dependency·설정 대상 파일 |
| 화면 | `Routy/AGENTS.md`, `Routy/INTEGRATION.md` | 요구사항·API·브라우저 테스트 관련 절 |
| 상태 감사 | `07-implementation-readiness.md` | 실제 코드와 git 상태 |

## 기준 간 관계

- 제품 수치는 요구사항·API·테스트·운영 문서 중 책임 문서에서 관리하고 다른 문서는 링크한다.
- `05`는 큰 방향, `11`은 실제 작업 순서다.
- `07`은 현재 상태, `10`은 완료 판정이다.
- Accepted ADR은 구현 완료를 뜻하지 않는다.
- 카카오 임시 사용 조건 확인과 실제 구현 완료를 구분한다. 좌표 기반 Place·estimate·완료 생성은 수명 계약을 구현하고 검증한 뒤에만 완료 처리한다.
- 계약이 바뀌면 영향받는 문서만 같은 Change Envelope에 포함한다.
