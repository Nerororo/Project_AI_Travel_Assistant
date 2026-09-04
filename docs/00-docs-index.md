# Travel 프로젝트 문서 안내

이 디렉터리는 `travel` 프로젝트의 요구사항, 아키텍처, 데이터베이스, API, 개발 계획, 설계 의사결정을 기록한다.

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
   - Phase별 개발 계획
   - 각 Phase의 학습 목표
   - 완료 조건
   - 구현 우선순위

6. `06-decisions.md`
   - 프로젝트에서 내린 주요 설계 결정
   - 선택 이유
   - 향후 재검토 조건

7. `07-implementation-readiness.md`
   - 코드와 문서의 현재 기준선
   - 구현 시작 전 확정할 계약과 운영 규칙
   - Codex 작업 단위별 완료 조건

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

## 문서 관리 원칙

- 실제 코드가 변경되면 관련 문서를 함께 수정한다.
- 아직 구현되지 않은 내용은 `계획` 또는 `예정`임을 명시한다.
- 설계가 변경되면 `06-decisions.md`에 이유를 남긴다.
- Codex는 `AGENTS.md`의 개발 규칙과 이 `docs/`의 설계를 함께 참고한다.
- 구현 시작 전에는 `07-implementation-readiness.md`의 기준선과 완료 조건을 확인한다.
- 실제 구현은 `11-command-roadmap.md`의 작업 ID를 한 번에 하나씩 진행한다.
