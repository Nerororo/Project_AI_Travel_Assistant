# Java Package Harness

## Scope

이 규칙은 `com.example.travel`과 모든 하위 Java 패키지에 적용한다. 하위 도메인 규칙을 함께 따른다.

## Package Map

- `TravelApplication`: 시작점만 담당하며 도메인 로직·임시 초기화를 두지 않는다.
- `controller`: 개발 확인 endpoint만 둔다. 업무 API는 도메인별 controller에 둔다.
- `user`, `region`, `ai`, `place`, `route`, `recommendation`, `travelplan`: 각 도메인 책임을 소유한다.
- `global`: 공통 예외·설정·보안·범용 도구만 소유한다.

## Layer Rules

- controller: DTO validation, Service 호출, HTTP 응답
- service: use case, 비즈니스 규칙, 트랜잭션 경계
- repository: 같은 도메인 저장소 접근
- domain: Entity·enum·값 객체, API와 외부 Client 비의존
- dto: API 입출력 또는 도메인 간 전달, DB 접근 금지
- algorithm: 결정적인 순수 계산, Spring·JPA·HTTP·AI 비의존
- client: 외부 통신 계약·구현, DB·추천 점수·일정 조합 금지

카카오 장소 Client는 `place/client`, 자동차·대중교통 Client는 `route/client`, OpenAI Client는 `ai/client`에 둔다. 호출량 예약과 `requestId` 상태는 MySQL 공유 저장소의 공개 계약을 사용하며 외부 호출 동안 트랜잭션을 유지하지 않는다.

다른 도메인은 공개 Service·DTO만 사용한다. `docs/03-database.md`에 정의된 Aggregate 관계에 한해 Entity 참조를 허용하되 다른 도메인 정책 호출 통로로 사용하지 않는다. 순환 의존이 생기면 공통 추상화를 만들기 전에 책임 배치를 재검토한다.
