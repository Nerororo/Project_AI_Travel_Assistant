# Routy - Development Instructions

## Project Goal

이 프로젝트는 Spring Boot 기반 AI 여행 일정 추천 및
경로 최적화 백엔드 서비스이다.

개발자는 Spring Boot 학습 단계의 취업 준비생이다.

프로젝트의 목적은 기능 완성뿐 아니라
Spring 아키텍처와 백엔드 설계를 학습하는 것이다.


## Core Principle

AI와 서버의 역할을 명확히 분리한다.

AI는 다음 역할을 담당한다.

- 국가를 입력했을 때 도시 후보 생성
- 도시 후보의 추천 이유 생성
- 음식 관련 자연어 분석

Spring Backend는 다음 역할을 담당한다.

- 데이터 저장
- Google 장소 검증과 Place ID 관리
- 여행 일정 관리
- 거리 계산
- 경로 최적화
- 호텔 추천 점수 계산
- 맛집 추천 점수 계산
- 인증/인가
- 비즈니스 규칙


## Development Rules

1. 사용자가 요청하지 않은 기능을 임의로 구현하지 않는다.

2. 기존 프로젝트 구조를 임의로 변경하지 않는다.

3. 코드를 작성하기 전에 어떤 파일을 수정할지 설명한다.

4. 새로운 Dependency를 추가할 경우 이유를 먼저 설명한다.

5. Controller에는 비즈니스 로직을 작성하지 않는다.

6. Controller는 HTTP Request/Response 처리를 담당한다.

7. Service는 비즈니스 로직을 담당한다.

8. Repository는 DB 접근을 담당한다.

9. Entity를 API Response로 직접 반환하지 않는다.

10. DTO와 Entity를 구분한다.

11. OpenAI API 관련 코드는 ai 패키지 내부에서 관리한다.

11-1. Google Places 관련 코드는 place 패키지의 client 내부에서 관리한다.

11-2. Google Routes 관련 코드는 route 패키지의 client 내부에서 관리한다.

11-2. Google Routes 관련 코드는 route 패키지의 client 내부에서 관리한다.

12. OpenAI API Key를 코드 또는 Git에 저장하지 않는다.

13. route 알고리즘은 AI에 의존하지 않는다.

14. 중요한 로직에는 테스트 코드를 작성한다. 테스트 책임과 완료 기준은 `docs/08-test-strategy.md`를 따른다.

15. 과도한 추상화나 복잡한 디자인 패턴을 사용하지 않는다.

16. 기능을 완료로 표시하기 전 `docs/10-definition-of-done.md`의 해당 체크리스트를 확인한다.

## Harness Rules

### Instruction Hierarchy

- 이 파일의 규칙은 프로젝트 전체에 적용한다.
- 하위 폴더의 `AGENTS.md`가 있으면 해당 폴더와 모든 하위 폴더의 작업에는 이 파일과 하위 규칙을 함께 적용한다.
- 하위 규칙은 상위 규칙을 구체화할 수 있지만, 보안·테스트·DTO 분리·AI와 서버의 책임 분리 원칙을 완화할 수 없다.

### Allowed Dependency Direction

- `controller`는 같은 도메인의 `service`, Request/Response DTO, `global`의 공통 예외 처리만 사용한다.
- `service`는 같은 도메인의 `domain`, `repository`, DTO, Client 인터페이스, 순수 알고리즘·정책 클래스와 명시적으로 필요한 다른 도메인의 공개 Service·전달용 DTO를 사용한다. 외부 제공자의 구체 HTTP 구현은 직접 생성하거나 참조하지 않는다.
- `repository`는 같은 도메인의 Entity 조회·저장만 담당하며 다른 도메인 Service를 호출하지 않는다.
- `domain` Entity와 값 객체는 Controller, API DTO, 외부 API Client에 의존하지 않는다.
- `dto`는 API 입출력 또는 도메인 간 전달용 데이터 표현만 담당하며 DB 조회·저장이나 비즈니스 로직을 두지 않는다.
- `global`에는 공통 예외, 설정, 보안, 범용 도구만 두며 특정 여행 도메인의 정책을 두지 않는다.
- `route/algorithm`은 Spring Bean, JPA Repository, HTTP Client, AI Client에 의존하지 않는 순수 Java 로직으로 유지한다.

### Required References Before Changes

| 변경 유형 | 먼저 읽을 문서 | 함께 확인할 위치 |
|---|---|---|
| 요구사항 또는 MVP 범위 | `docs/01-requirements.md` | 관련 도메인 `AGENTS.md` |
| Controller, DTO, HTTP 상태 코드 | `docs/04-api-spec.md` | `global` 예외 규칙 |
| Entity, Repository, migration, DB 제약 | `docs/03-database.md` | `docs/04-api-spec.md` |
| 경로·거리·추천 점수 알고리즘 | `docs/01-requirements.md`, `docs/08-test-strategy.md` | `route` 또는 `recommendation` 규칙 |
| AI Client, prompt, provider 설정 | `docs/04-api-spec.md`, `docs/09-operations.md` | `ai/AGENTS.md` |
| Google Places Client, Place ID, 지도 표시 | `docs/04-api-spec.md`, `docs/09-operations.md` | `place/AGENTS.md`, `resources/static/AGENTS.md` |
| Google Routes Client, 이동 시간 행렬 | `docs/04-api-spec.md`, `docs/09-operations.md` | `route/AGENTS.md` |
| 테스트 추가 또는 수정 | `docs/08-test-strategy.md` | `docs/10-definition-of-done.md` |
| 설정, 배포, Docker, 외부 API | `docs/09-operations.md` | 관련 `build.gradle` 또는 설정 파일 |

### Change Boundaries

- 한 작업은 하나의 기능 또는 설계 변경 단위로 제한한다. 관련 없는 리팩터링, 포맷 변경, 파일 이동을 함께 수행하지 않는다.
- 모든 작업은 수정 전에 `docs/12-harness-boundaries.md`의 단계별 경계를 확인하고, 작업 ID·Allowed Paths·Conditional Paths·Forbidden Paths를 포함한 Change Envelope를 사용자에게 설명한다.
- 명시되지 않은 경로는 미승인 경로로 취급하며 수정하지 않는다. 현재 작업에 필요하면 수정 전에 이유·영향·추가할 파일을 설명하고 사용자 승인을 받아 Change Envelope의 Allowed 또는 Conditional Paths에 추가할 수 있다.
- Conditional Paths는 최초 또는 추가 승인된 Change Envelope에 파일과 변경 조건이 명시되고, 해당 조건이 충족된 경우에만 수정한다.
- 단계표·적용 규칙·Change Envelope에 명시된 Forbidden Paths는 같은 작업 안에서 수정하거나 미승인 경로로 재분류하지 않는다. 필요하면 현재 작업을 끝내고 별도 작업 단위로 제안한다.
- API, DB 스키마, 알고리즘, AI 계약을 변경하면 해당 계약 문서도 같은 작업에서 갱신한다.
- versioned migration은 적용된 파일을 수정하지 않고 새 버전 파일을 추가한다.
- 비밀값, 개인 정보, API 원문 요청·응답은 코드, Git, fixture, 문서 예시, 로그, 오류 응답에 남기지 않는다.
- 다른 도메인의 Repository나 내부 구현 클래스를 직접 참조하지 않는다. 필요한 기능은 해당 도메인의 Service 계약을 통해 사용한다.
- `docs/03-database.md`에 명시된 Aggregate 간 JPA 연관관계는 Entity 참조를 허용한다. 이 참조는 관계 매핑과 데이터 탐색에만 사용하며 다른 도메인의 비즈니스 규칙을 호출하는 통로로 사용하지 않는다.
- 현재 작업과 관계없는 `docs/`, `src/main/resources/`, 빌드 설정, 템플릿 자산은 변경하지 않는다.

### Verification Gate

- 구현 전에는 수정 대상 파일, 변경 이유, 읽은 기준 문서를 설명한다.
- 구현 전 `git status --short`와 변경 대상 파일의 기존 diff를 확인해 사용자 변경을 식별한다. 기존 변경을 되돌리거나 덮어쓰지 않는다.
- 구현 후 실제로 수정한 파일 목록을 Change Envelope와 대조한다. 범위 밖 변경이 있으면 완료로 보고하지 말고 원인을 밝힌다.
- 사용자가 요청하지 않으면 `git add`, commit, stash, reset을 실행하지 않는다.
- 구현 후에는 관련 테스트와 `./gradlew test` 결과를 확인한다. 실행할 수 없는 경우에는 이유와 미검증 범위를 명확히 남긴다.
- 기능 완료 표시는 `docs/10-definition-of-done.md`의 공통 항목과 해당 도메인 항목을 확인한 뒤에만 한다.

### Infrastructure File Boundaries

- `build.gradle`, `settings.gradle`은 dependency·빌드 규칙 변경이 필요한 경우에만 수정한다. dependency 추가 전에는 목적과 대안을 설명한다.
- `docker-compose.yaml`은 로컬 개발용 인프라 설정이다. 서비스명, 포트, volume, 환경 변수 이름을 관련 문서 갱신 없이 임의로 변경하지 않는다.
- `src/main/resources/application*.yml`, `application*.properties`에는 비밀값을 기록하지 않는다. profile·외부 연결·로그 정책 변경 전에는 `docs/09-operations.md`를 확인한다.
- `src/main/resources/db/migration`이 생성된 뒤에는 migration 파일을 versioned schema 변경 전용으로 사용한다. 적용 이력이 있는 migration은 수정하지 않는다.


## Learning Rule

Spring 관련 새로운 개념을 사용하는 경우
구현과 함께 해당 개념을 설명한다.

특히 다음 개념을 처음 사용할 때 설명한다.

- Bean
- Dependency Injection
- IoC
- Controller
- Service
- Repository
- JPA
- Entity
- DTO
- Transaction
- Lazy Loading
- Validation
- Exception Handler
- Spring Security
- JWT
