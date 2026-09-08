# Java Package Harness

## Scope

이 규칙은 `com.example.travel`과 모든 하위 Java 패키지에 적용한다. 하위 도메인의 `AGENTS.md`가 있으면 해당 규칙을 추가로 적용한다.

## Package Map

- `TravelApplication`: Spring Boot 애플리케이션 시작점이다. 도메인 로직이나 임시 초기화 코드를 두지 않는다.
- `controller`: 현재 개발 확인용 HTTP endpoint만 둔다. 새 업무 API는 해당 도메인의 `controller` 패키지에 둔다.
- `ai`, `place`, `recommendation`, `route`, `travelplan`, `user`: 도메인별 기능을 소유한다.
- `global`: 예외 처리, 설정, 보안, 공통 응답만 소유한다.

## Spring Layer Rules

- `controller`: 요청 DTO 검증, Service 호출, HTTP 상태·응답 DTO 생성만 담당한다. Entity, Repository, 알고리즘을 직접 사용하지 않는다.
- `service`: 유스케이스와 트랜잭션 경계를 담당한다. Controller 객체를 받거나 HTTP 응답을 만들지 않는다.
- `repository`: Spring Data JPA 등 저장소 접근만 담당한다. 서비스 조합, 외부 API 호출, DTO 변환을 두지 않는다.
- `domain`: Entity, enum, 값 객체, 도메인 규칙을 둔다. API DTO·Controller·Repository에 의존하지 않는다.
- `dto`: 요청/응답 또는 도메인 간 전달 데이터다. validation annotation 외의 비즈니스 처리와 영속성 접근을 두지 않는다.
- `algorithm`: 결정적이고 테스트 가능한 계산만 둔다. Spring·JPA·HTTP·AI 의존성을 추가하지 않는다.
- `client`: 외부 제공자 통신 계약만 담당한다. 점수 계산, DB 저장, 일정 조합을 하지 않는다.

## Cross-Domain Rule

- 다른 도메인의 Repository나 내부 구현 클래스를 직접 사용하지 않는다.
- `docs/03-database.md`에 명시된 Aggregate 간 JPA 연관관계에 한해 다른 도메인의 Entity 참조를 허용한다. 연관 Entity의 상태를 이용해 그 도메인의 정책을 대신 구현하지 않는다.
- 다른 도메인 기능이 필요하면 공개된 Service 계약 또는 전용 DTO를 사용한다.
- 순환 의존이 생기면 새 공통 추상화를 즉시 만들지 말고, 먼저 책임이 잘못 배치됐는지 검토하고 `docs/06-decisions.md`에 설계 결정을 기록한다.
