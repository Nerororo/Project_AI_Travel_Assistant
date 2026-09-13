# Routy - Development Instructions

## Project Goal

Routy는 국내 여행 장소를 선택하고 자동차 또는 대중교통의 이동 시간을 반영해 일정을 만드는 Spring Boot 백엔드 학습 프로젝트다. 기능 완성과 함께 Spring 계층, 도메인 경계, 테스트와 운영 설계를 학습한다.

## Core Principle

AI와 서버의 책임을 분리한다.

AI:
- 허용된 국내 지역 목록에서 지역 후보 3개와 이유 생성
- 지역·관광지 맥락을 반영해 음식 자연어를 메뉴·검색어·이유·선택적 기준 관광지 1~5개로 구조화

Spring Backend:
- 인증·인가와 사용자별 호출 한도
- 국내 지역 기준 데이터와 검색
- 카카오 장소 검색 결과 검증과 데이터 수명 통제
- 거리, 방문 순서, 체류·이동 시간과 일정 계산
- 숙소 지도 탐색 중심과 음식점 추천 점수
- 완료 일정 저장·조회·제한 편집·공유
- 비즈니스 규칙과 외부 장애 처리

AI는 장소 존재, 거리, 방문 순서, 시간표, 추천 점수와 저장 성공을 결정하지 않는다.

## Development Rules

1. 사용자가 요청하지 않은 기능과 구조 변경을 추가하지 않는다.
2. 구현 전에 수정 파일, 이유와 Change Envelope를 설명한다.
3. dependency 추가 전 목적과 대안을 설명한다.
4. Controller는 HTTP Request·Response, DTO validation과 Service 호출만 담당한다.
5. Service는 비즈니스 규칙과 use case 조합을 담당한다.
6. Repository는 같은 도메인의 DB 접근만 담당한다.
7. Entity와 API DTO를 분리하며 Entity를 직접 반환하지 않는다.
8. 외부 HTTP 구현은 OpenAI=`ai/client`, 카카오 장소=`place/client`, 경로=`route/client`에 둔다.
9. `route/algorithm`은 Spring·JPA·HTTP·AI에 의존하지 않는 순수 Java로 유지한다.
10. 다른 도메인은 공개 Service·전달 DTO로만 사용하고 Repository·내부 구현을 직접 참조하지 않는다.
11. 외부 API 호출 중 DB 트랜잭션을 열지 않는다.
12. 완료 일정은 모든 계산 뒤 짧은 트랜잭션으로 Aggregate 전체를 저장한다.
13. 적용된 versioned migration은 수정하지 않고 새 버전을 추가한다.
14. 비밀값, 개인정보, 사용자 원문, 외부 원문과 카카오 좌표를 코드·Git·fixture·로그·오류 응답에 남기지 않는다.
15. 중요한 로직은 `docs/08-test-strategy.md`에 따라 테스트한다.
16. 과도한 추상화와 복잡한 패턴을 사용하지 않는다.
17. 완료 표시는 `docs/10-definition-of-done.md` 확인 뒤에만 한다.

## Product Invariants

- 서울특별시·광역시·세종특별자치시는 자체를 최종 여행 지역으로 선택하고, 도·특별자치도는 그 아래 시·군 하나를 선택한다. 특별시·광역시 아래 구·군은 장소 검색 필터로만 사용하며 읍·면·동과 해외는 제외한다.
- 특별시·광역시·세종특별자치시의 대표 좌표 반경 20km는 초기 장소 검색 범위이며 도시 전체 경계로 간주하지 않는다. 지역명 결합 검색과 주소 행정구역 검증으로 도시 전체 검색을 보완한다.
- 숙소는 기하 중앙값·메도이드·현재 지도 영역으로 탐색하되 거리 점수로 자동 추천하지 않고 사용자가 지도에서 직접 선택한다.
- 일정 하나는 `CAR` 또는 `PUBLIC_TRANSIT` 하나만 사용한다.
- Haversine·Nearest Neighbor·2-opt로 먼저 계산하고 최종 후보 인접 구간만 실제 경로로 검증한다.
- 이동 시간은 10분 단위로 올리며 고정 이동 buffer를 더하지 않는다.
- 경로 API가 반환한 예상 이동시간으로 종료 시각을 넘으면 저장하지 않고 장소 삭제·체류 축소도 자동 수행하지 않는다.
- 장소 유형은 기본 체류 시간 계산에만 쓰고 노출·저장하지 않는다.
- 카카오 장소명을 사용자 이름의 기본값·placeholder로 사용하지 않는다.
- 완료 뒤에는 제목·사용자 장소 이름·메모만 수정한다.
- 완료·공유 조회는 외부 API와 지도 없이 저장 데이터·카카오 링크만 사용한다.
- 여행은 1~7일이며 하루 관광지는 최대 5개다.
- 점심은 11:30~14:00, 저녁은 17:30~20:30 안에 60분으로 배치하고 한쪽 이동 여유 기본 15분을 중복 없이 반영한다.
- 사용자·서비스 호출 한도는 요구사항·API·운영 문서의 책임 계약을 따른다. 필요한 경로 쿼터를 사전 확보하지 못하면 외부 호출 없이 일정 전체를 Haversine으로 추정하고 warning을 반환한다.
- 호출 카운터와 `requestId` 처리 상태는 MySQL 공유 저장소에 두고 좌표·결과 리소스 ID·payload·response를 저장하지 않는다. 같은 requestId가 처리 중이면 `REQUEST_IN_PROGRESS`, 이미 성공했으면 `REQUEST_ALREADY_COMPLETED`로 차단하며 외부 호출·저장·호출량 차감을 반복하지 않는다.

## Kakao Policy Gate

`K0-02A`는 2026-09-11 카카오 DevTalk 답변으로 완료되었다. 실제 카카오 좌표 기반 장소 선택, `selectionToken`, estimate·완료 생성과 경로 호출은 좌표를 한 번의 제작 흐름과 서버 요청에서만 일시적으로 사용하고 즉시 폐기해야 한다. 정책 확인을 구현 완료로 간주하지 않는다.

## Harness Rules

- 루트 규칙은 전체 저장소에 적용되고 하위 `AGENTS.md`는 이를 구체화한다.
- 작업 전 `docs/11-command-roadmap.md`의 작업 ID와 `docs/12-harness-boundaries.md`의 해당 단계 행을 확인한다.
- 적용되는 AGENTS, 대상 파일과 직접 참조 코드, 필요한 문서 절만 읽는다.
- Change Envelope에는 Task ID, Goal, Allowed·Conditional·Forbidden Paths, References Read, Verification을 적는다.
- 명시되지 않은 경로는 미승인이다. 필요하면 편집 전에 이유·영향·파일을 설명하고 승인받는다.
- 명시적 Forbidden은 같은 작업에서 재분류하지 않고 별도 작업으로 다룬다.
- 작업 전 `git status --short`와 대상 diff로 사용자 변경을 확인한다.
- 작업 후 실제 변경 경로, 관련 테스트, 전체 `./gradlew test`, `git diff --check`와 DoD를 확인한다.
- 문서 전용 작업은 Gradle 테스트 생략 이유를 기록한다.
- 사용자가 요청하지 않으면 git add, commit, stash, reset, checkout을 실행하지 않는다.

## Required References

| 변경 | 먼저 읽을 문서 |
|---|---|
| 요구사항·MVP | `docs/01-requirements.md` 관련 절 |
| 아키텍처·도메인 책임 | `docs/02-architecture.md` |
| Entity·Repository·migration | `docs/03-database.md` 관련 절 |
| Controller·DTO·상태 코드 | `docs/04-api-spec.md` 해당 endpoint |
| 알고리즘·추천 | `docs/01-requirements.md`, `docs/08-test-strategy.md` 관련 절 |
| AI·카카오 Client | `docs/04-api-spec.md`, `docs/09-operations.md` |
| 테스트 | `docs/08-test-strategy.md`, 완료 시 `docs/10-definition-of-done.md` |
| 설정·배포 | `docs/09-operations.md` |

## Infrastructure

- 빌드 파일은 dependency·빌드 변경에만 수정한다.
- `application*`에 비밀값을 기록하지 않는다.
- Docker의 서비스명·포트·volume·환경 변수는 운영 문서 없이 바꾸지 않는다.
- migration은 schema 변경 전용이다.

## Learning Rule

처음 사용하는 Bean, DI, IoC, Controller, Service, Repository, JPA, Entity, DTO, Transaction, Lazy Loading, Validation, Exception Handler, Spring Security와 JWT는 구현과 함께 초보자 기준으로 설명한다.
