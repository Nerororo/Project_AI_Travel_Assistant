# Routy 프로젝트 문서 안내

이 디렉터리는 Routy 프로젝트의 요구사항, 아키텍처, 데이터베이스, API, 개발 계획, 설계 의사결정을 기록한다.

## 현재 프로젝트 기준

- Language: Java 21
- Framework: Spring Boot 4.1.1
- Build Tool: Gradle
- Database: MySQL 8.4
- ORM: Spring Data JPA
- Base Package: `com.example.travel`
- Local DB: Docker Compose
- 현재 확인된 API: `GET /hello`
- AI: OpenAI API 연동 예정

## 문서 구성

1. `01-requirements.md`
   - 서비스 목표
   - 기능 요구사항
   - MVP 범위
   - 비기능 요구사항

2. `02-architecture.md`
   - 전체 시스템 구조
   - Spring 내부 계층 구조
   - 도메인별 책임
   - AI와 백엔드의 책임 분리

3. `03-database.md`
   - Entity / Table 설계
   - 관계
   - 초기 MVP 데이터 모델
   - 향후 확장 방향

4. `04-api-spec.md`
   - REST API 목록
   - 주요 Request / Response 예시
   - API 개발 순서

5. `05-development-plan.md`
   - 전체 개발 방향
   - 단계별 목표와 학습 주제
   - 세부 작업과 상태는 기록하지 않음

6. `06-decisions.md`
   - 프로젝트에서 내린 주요 설계 결정
   - 선택 이유
   - 향후 재검토 조건

7. `07-implementation-readiness.md`
   - 코드와 문서의 현재 기준선
   - 구현 전에 남은 미확정 사항
   - 완료 조건은 기록하지 않음

8. `08-test-strategy.md`
   - 단위·통합·API 테스트의 책임 경계
   - 외부 AI·지도 클라이언트 대체 규칙
   - 도메인별 필수 테스트와 완료 기준

9. `09-operations.md`
   - local·test·prod 환경 운영 기준
   - 로그, 헬스체크, 외부 API 장애·비용 관리
   - 배포 전후 검증 체크리스트

10. `10-definition-of-done.md`
    - 기능 완료를 판단하는 공통 체크리스트
    - 도메인·API·AI·DB 변경별 완료 조건

11. `11-command-roadmap.md`
    - Codex와 대화하며 진행하는 작은 구현 단위
    - 작업별 요청 문구, 예상 변경 범위, 완료 확인 방법

## 선택적 읽기 규칙

Codex와 개발할 때 `docs/` 전체를 한 번에 읽지 않는다. 적용되는 `AGENTS.md`와 현재 작업에 필요한 아래 문서만 읽는다.

| 작업 유형 | 반드시 읽을 문서 | 조건부로 읽을 문서 |
|---|---|---|
| 작업 선택·순서 확인 | `11-command-roadmap.md`의 해당 작업 | 큰 방향이 필요하면 `05-development-plan.md` |
| 도메인 규칙 변경 | `01-requirements.md`의 해당 절 | 책임 경계가 바뀌면 `02-architecture.md` |
| Entity·migration·query | `03-database.md`의 해당 절 | API도 바뀌면 `04-api-spec.md` |
| Controller·DTO·오류 응답 | `04-api-spec.md`의 해당 절 | 새 요구사항이면 `01-requirements.md` |
| 테스트 작성·수정 | `08-test-strategy.md`의 해당 절 | 완료 판정 시 `10-definition-of-done.md` |
| AI·외부 API·profile·배포 | `09-operations.md`의 해당 절 | HTTP 계약도 바뀌면 `04-api-spec.md` |
| 설계 선택 변경 | `06-decisions.md` | 영향받은 계약 문서 |
| 새 Phase 시작·현황 점검 | `07-implementation-readiness.md` | 실행 작업은 `11-command-roadmap.md` |

기능 구현을 마칠 때만 `10-definition-of-done.md`의 공통 항목과 해당 도메인 항목을 확인한다. 관련 없는 문서는 읽거나 갱신하지 않는다.

## 단일 기준 문서

- `05-development-plan.md`는 큰 방향, `11-command-roadmap.md`는 실제 작업 순서와 현재 작업의 기준이다.
- `07-implementation-readiness.md`는 현재 구현 상태, `10-definition-of-done.md`는 완료 판정의 기준이다.
- 요구사항·DB·API·테스트·운영 수치는 각각 `01`, `03`, `04`, `08`, `09` 문서에만 상세히 기록한다.

## 문서 관리 원칙

- 실제 코드가 변경되면 선택적 읽기 표에서 직접 관련된 문서만 함께 수정한다.
- 아직 구현되지 않은 내용은 `계획` 또는 `예정`임을 명시한다.
- 설계가 변경되면 `06-decisions.md`에 이유를 남긴다.
- Codex는 적용되는 `AGENTS.md`와 현재 작업에 필요한 문서만 참고한다.
- 구현 시작 전에는 `07-implementation-readiness.md`의 기준선과 완료 조건을 확인한다.
- 실제 구현은 `11-command-roadmap.md`의 작업 ID를 한 번에 하나씩 진행한다.
