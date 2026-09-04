# Definition of Done

## 1. 목적

이 문서는 기능이 단순히 구현된 상태와, 요구사항·테스트·문서·운영 기준까지 갖춘 완료 상태를 구분한다.

기능이 아래 조건을 만족하기 전에는 README나 개발 계획의 체크박스를 완료로 바꾸지 않는다.

## 2. 모든 기능의 공통 완료 조건

- [ ] 관련 요구사항, API, DB, ADR 문서의 현재 기준을 확인했다.
- [ ] 변경 범위가 한 도메인 또는 한 API 흐름으로 제한되어 있다.
- [ ] Controller, Service, Repository, Entity, DTO 책임이 프로젝트 규칙에 맞게 분리되어 있다.
- [ ] 성공 시나리오와 최소 한 개의 실패 또는 경계 시나리오를 자동 테스트했다.
- [ ] API가 있으면 성공·validation·not found 응답이 명세와 일치한다.
- [ ] `./gradlew test`가 통과한다.
- [ ] API, DB 스키마, 설정, 알고리즘이 바뀌었다면 관련 문서를 갱신했다.
- [ ] 설계 선택이 바뀌었다면 ADR을 추가하거나 기존 ADR의 재검토 상태를 갱신했다.
- [ ] 로그·오류 응답·테스트 fixture에 비밀값이나 사용자 원문이 없다.

## 3. Place CRUD 완료 조건

- [ ] 장소 등록·목록·상세·수정·삭제가 구현됐다.
- [ ] name, type, region, 좌표의 validation 규칙이 API 명세와 일치한다.
- [ ] page, size, sort의 기본값·경계값·잘못된 값이 테스트됐다.
- [ ] 존재하지 않는 장소는 `PLACE_NOT_FOUND`와 `404`를 반환한다.
- [ ] 참조 중인 Place 삭제는 `409 Conflict`로 거절한다.
- [ ] Place 테이블의 필수 제약조건과 인덱스가 migration에 반영됐다.
- [ ] Controller API 테스트와 DB 통합 테스트가 통과한다.

## 4. TravelPlan 완료 조건

- [ ] 시작일·종료일, 장소 중복, 필수 장소, 일일 최대 6개 장소 제약이 검증된다.
- [ ] 필수 장소가 최종 일정에서 누락되지 않는다.
- [ ] 날짜별 장소 수 차이가 최대 1이며, 날짜별 경로만 최적화한다.
- [ ] 저장·수정·삭제가 Aggregate 생명주기와 DB 삭제 정책을 지킨다.
- [ ] 실패 시 일부 Day·Place 관계만 남지 않도록 트랜잭션을 검증했다.
- [ ] 응답의 `assumptions`, 숙소, 음식점 후보가 API 계약과 일치한다.
- [ ] 생성·조회·수정·삭제 API와 핵심 통합 테스트가 통과한다.

## 5. Route와 Recommendation 완료 조건

- [ ] 거리 계산은 단위와 반올림 규칙을 포함해 단위 테스트됐다.
- [ ] RouteOptimizer는 빈·단일·복수 장소와 동률 거리에서 결정적으로 동작한다.
- [ ] 모든 장소를 중복 없이 한 번씩 방문한다.
- [ ] 2-opt를 추가했다면 기존 경로보다 길어지지 않는 테스트가 있다.
- [ ] 호텔은 총 이동 거리, 음식점은 음식 일치와 동선 이탈 거리 기준으로 정렬된다.
- [ ] 알고리즘은 Spring, DB, AI에 직접 의존하지 않는 순수 Java 로직으로 유지된다.

## 6. AI 기능 완료 조건

- [ ] `AiClient`와 provider HTTP 구현이 분리되어 있다.
- [ ] JSON Schema/structured output과 Java DTO validation을 모두 적용한다.
- [ ] 허용되지 않은 enum, 누락 필드, JSON 파싱 실패가 `AI_RESPONSE_INVALID`으로 변환된다.
- [ ] timeout·provider 5xx는 최대 한 번 재시도한 뒤 `AI_UNAVAILABLE`으로 변환된다.
- [ ] 인증 오류와 요청 오류는 재시도하지 않는다.
- [ ] fake `AiClient`만으로 성공·계약 위반·장애 테스트가 통과한다.
- [ ] 자동 테스트는 실제 OpenAI를 호출하지 않는다.
- [ ] API 키, 사용자 원문, provider 원문 요청·응답이 로그 또는 오류 응답에 없다.

## 7. DB와 migration 변경 완료 조건

- [ ] 새 테이블·컬럼·인덱스·제약조건은 versioned Flyway migration으로 추가했다.
- [ ] 이미 적용된 migration 파일을 수정하지 않았다.
- [ ] 빈 DB에서 migration 적용을 검증했다.
- [ ] UNIQUE, CHECK, FK 등 의도한 DB 무결성 제약을 통합 테스트했다.
- [ ] 파괴적 변경이면 데이터 백업, 호환 기간, 복구 절차를 별도 계획에 기록했다.
- [ ] 운영 profile에서 `ddl-auto: validate`로 schema 일치를 확인한다.

## 8. 외부 API와 운영 변경 완료 조건

- [ ] 실제 외부 API 호출은 자동 테스트와 분리됐다.
- [ ] timeout, 재시도 대상, 오류 코드 변환을 명시하고 테스트했다.
- [ ] 호출량·오류율은 원문 payload 없이 집계할 수 있다.
- [ ] health check에서 앱·DB와 외부 의존성 상태를 구분한다.
- [ ] profile별 비밀값과 로그 정책을 확인했다.
- [ ] 배포 전후 체크리스트는 `docs/09-operations.md`를 따른다.

## 9. 완료 기록 방법

기능 완료 시 다음을 함께 갱신한다.

1. README와 `05-development-plan.md`의 진행 상태
2. API, DB, 아키텍처 문서 중 변경된 계약
3. 새 설계 결정이 있다면 `06-decisions.md`
4. 테스트 실행 결과와 수동 smoke test 결과(수행했다면)

커밋은 하나의 기능 또는 설계 변경을 설명할 수 있는 작은 단위로 준비한다. 이 문서는 커밋 자체를 강제하지 않지만, 완료된 변경이 무엇인지 추적 가능해야 한다.
