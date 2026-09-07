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

- 자연어 여행 조건 분석
- 사용자 여행 선호 분석
- 여행지 후보 추천
- 음식 관련 자연어 분석

Spring Backend는 다음 역할을 담당한다.

- 데이터 저장
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

12. OpenAI API Key를 코드 또는 Git에 저장하지 않는다.

13. route 알고리즘은 AI에 의존하지 않는다.

14. 중요한 로직에는 테스트 코드를 작성한다. 테스트 책임과 완료 기준은 `docs/08-test-strategy.md`를 따른다.

15. 과도한 추상화나 복잡한 디자인 패턴을 사용하지 않는다.

16. 기능을 완료로 표시하기 전 `docs/10-definition-of-done.md`의 해당 체크리스트를 확인한다.


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
