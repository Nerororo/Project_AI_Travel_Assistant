# 개발 계획

## 1. 문서 역할

이 문서는 프로젝트의 큰 개발 방향과 단계별 학습 목표를 관리한다.

- 세부 작업 ID와 실행 순서: docs/11-command-roadmap.md
- 현재 구현 준비도: docs/07-implementation-readiness.md
- 테스트 책임: docs/08-test-strategy.md
- 완료 판정: docs/10-definition-of-done.md
- 선택 이유: docs/06-decisions.md

세부 endpoint, 테이블, 오류 코드, 작업 상태를 이 문서에 중복 기록하지 않는다.

---

## 2. 개발 전략

Routy는 Spring Boot 학습과 포트폴리오 완성을 함께 목표로 한다. 큰 기능을 한 번에 구현하지 않고 다음 흐름을 반복한다.

~~~text
문서 계약 확인
→ 작은 작업의 책임 설명
→ 구현
→ 관련 테스트
→ 전체 회귀
→ 완료 기준 확인
→ 다음 작업
~~~

원칙:

- 한 작업에서 하나의 기능 또는 설계 변경만 다룬다.
- 외부 API보다 인터페이스와 fake를 먼저 구현한다.
- Controller, Service, Repository, DTO, Entity, Client, 순수 알고리즘의 책임을 분리한다.
- 외부 호출 중 DB 트랜잭션을 열지 않는다.
- 측정하지 않은 성능·정확도·개선율을 문서나 포트폴리오에 쓰지 않는다.
- 카카오 좌표를 쓰는 기능은 2026-09-11 확인된 일시 사용·즉시 폐기 조건을 구현과 테스트에서 검증한다.

---

## 3. 단계별 방향

| 단계 | 목표 | 주요 학습 |
|---|---|---|
| 문서 기준 정렬 | 국내·카카오·새 저장 및 API 계약 통일 | 요구사항 추적, ADR, 변경 경계 |
| 개발 기반 | JPA·MySQL·Flyway·profile·테스트 기반 | Dependency, Configuration, migration |
| 인증 기반 | User, Spring Security, JWT, 소유권 | 인증·인가, 비밀번호 해시, 보안 테스트 |
| 지역 기준 | regions.json 로딩, 직접 검색, AI 허용 목록 | Bean, Validation, 정적 기준 데이터 |
| 순수 경로 | Haversine, Nearest Neighbor, 2-opt | 순수 Java, 결정적 알고리즘, 복잡도 |
| 외부 Client 계약 | Place·Route·AI 인터페이스와 fake | DI, Adapter 경계, provider 오류 변환 |
| 장소 제작 흐름 | 카카오 검색과 selectionToken | 서버측 API 키, 무결성, 데이터 수명 |
| 독립 경로 기반 | 정렬된 자동차·대중교통 구간 검증 | Client 분리, 재시도, 호출량 통제 |
| 추정 일정 계산 | 체류·식사·Haversine 이동과 날짜별 시간 예산 | Service 조합, 정책 클래스, 실패 모델 |
| 탐색·추천 | 지도 기반 숙소 탐색과 음식점 후보 평가 | 기하 중앙값·메도이드, 지도 검색, 점수·정렬 경계 |
| 외부 경로 통합·완료 일정 저장 | 예상 이동시간 재검증과 TravelPlan Aggregate 원자 저장 | 도메인 Service 조합, Entity, Repository, Transaction, Lazy Loading |
| 조회·편집·공유 | 무외부호출 조회와 제한 수정 | DTO projection, 소유권, 읽기 토큰 |
| 서비스 화면 | 제작 지도와 완료 템플릿 연결 | 상태 관리, API 연동, 접근성, 오류 UX |
| 품질·운영 | 로그·health·쿼터·배포 기준 | Exception Handler, 관찰성, 운영 검증 |

세부 작업은 이 표의 큰 방향을 docs/11-command-roadmap.md에서 더 작은 작업 ID로 나눈다.

---

## 4. 선행 관계

### 인증을 먼저 두는 이유

AI·카카오 호출에는 사용자별 한도가 필요하고 일정에는 처음부터 소유권이 있어야 한다. 인증을 나중에 추가하면 API와 migration을 반복 변경하게 되므로 개발 기반 직후에 구현한다.

### 순수 알고리즘을 외부 Client보다 먼저 두는 이유

Haversine, Nearest Neighbor, 2-opt는 카카오 없이 구현하고 검증할 수 있다. 외부 Client와 분리하면 확정된 좌표 수명 계약도 더 쉽게 검증할 수 있다.

### fake를 먼저 두는 이유

외부 API를 자동 테스트에서 호출하면 키·비용·네트워크 때문에 결과가 불안정하다. Service는 Client 인터페이스와 fake로 계약을 검증한 후 실제 HTTP 구현을 연결한다.

### 저장을 계산 뒤에 두는 이유

TravelPlan DB에는 완료된 일정만 저장한다. 먼저 완성된 계산 결과의 형태와 검증 규칙을 고정한 뒤 Aggregate를 구현해야 임시 시각이나 불필요한 nullable 컬럼을 만들지 않는다.

### 화면을 얇게 연결하는 이유

백엔드 전체가 끝날 때까지 기다리지 않는다. 지역 검색, 장소 선택, 일정 계산, 완료 조회가 준비되는 시점마다 해당 화면만 fake 또는 테스트 환경에 연결한다. 완료 화면은 외부 API 없이 저장 DTO만으로 렌더링한다.

---

## 5. 카카오 답변 반영과 정책 변경 대응

### 확인된 답변 적용

- 브라우저 메모리 기반 제작 상태와 selectionToken 계약을 유지한다.
- Place 검색, 일정 estimate, 최종 생성 순서로 구현한다.
- 서버 요청 종료와 브라우저 제작 종료 시 외부 데이터를 폐기하는 테스트를 추가한다.

### 향후 정책 변경 시

- 카카오 좌표를 이용하는 Place 제작 구현을 시작하지 않는다.
- 장소·좌표 원천 또는 제품 범위를 다시 결정한다.
- requirements, database, API, ADR, 테스트·운영·로드맵을 새 결정에 맞게 갱신한다.
- 이미 작성한 목표 계약을 임시 우회 구현하지 않는다.

정책이 바뀌더라도 개발 기반, 인증, 지역 기준 데이터와 순수 알고리즘은 외부 좌표 계약과 분리해 유지한다.

---

## 6. 작업 루틴

1. 로드맵에서 작업 ID 하나를 선택한다.
2. 적용되는 AGENTS.md와 직접 관련된 기준 문서만 읽는다.
3. 수정 파일, 이유, Allowed·Conditional·Forbidden Paths를 설명한다.
4. 새로운 Spring 개념과 클래스 책임을 구현 전에 설명한다.
5. 사용자 변경이 있는지 git status와 대상 diff를 확인한다.
6. 구현하고 필요한 단위·통합·HTTP 테스트를 실행한다.
7. 전체 테스트와 git diff --check를 확인한다.
8. Definition of Done의 공통·도메인 항목을 확인한다.
9. 실제 변경 파일이 Change Envelope 안인지 대조한다.
10. 구현 상태와 직접 바뀐 계약 문서만 갱신한다.

사용자가 요청하지 않으면 git add, commit, stash, reset을 실행하지 않는다.

---

## 7. 학습 목표

| 영역 | 학습할 핵심 |
|---|---|
| Spring Bean·DI·IoC | Service와 Client 인터페이스가 연결되는 과정 |
| Controller·DTO·Validation | HTTP 입력과 도메인 규칙의 경계 |
| Service | 일정 생성과 여러 도메인 계약 조합 |
| Repository·JPA·Entity | 완료 일정 Aggregate 저장과 조회 |
| Transaction | 외부 호출 뒤 전체 일정의 원자 저장 |
| Lazy Loading | 조회 쿼리와 N+1 확인 |
| Exception Handler | 도메인 실패를 일관된 HTTP 오류로 변환 |
| Spring Security·JWT | 인증 사용자와 일정 소유권 |
| 순수 알고리즘 | 프레임워크 없이 거리·순서 로직 검증 |
| 외부 Client | timeout, 재시도, 쿼터, fake 경계 |

---

## 8. 포트폴리오 측정

실제 구현과 테스트에서 얻은 값만 기록한다.

- Nearest Neighbor와 2-opt의 거리 및 개선율
- 최대 입력에서 알고리즘 실행시간과 복잡도 설명
- 실제 경로 검증 전후 10분 단위 시간표 변화
- 외부 호출 수와 변경 구간 재조회 효과
- AI 구조화 응답 성공·실패·재시도 유형
- 주요 JPA query와 N+1 확인 결과
- 핵심 단위·통합·HTTP·브라우저 테스트 결과
- 호출 한도와 중복 요청 차단 검증 결과

카카오 정책 준수 여부나 정확도를 자체 추측한 성과로 표현하지 않는다.
