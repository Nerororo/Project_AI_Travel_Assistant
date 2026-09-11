# 구현 준비도와 현재 기준선

## 1. 문서 역할

이 문서는 실제 코드와 설정, 문서 정렬 상태, 구현 가능 여부와 차단 조건을 기록한다. 목표 요구사항이나 완료 체크리스트를 복제하지 않는다.

- 제품 범위: `docs/01-requirements.md`
- 아키텍처: `docs/02-architecture.md`
- DB·HTTP 계약: `docs/03-database.md`, `docs/04-api-spec.md`
- 결정 이유: `docs/06-decisions.md`
- 테스트·운영: `docs/08-test-strategy.md`, `docs/09-operations.md`
- 실행 순서: `docs/11-command-roadmap.md`
- 완료 판정: `docs/10-definition-of-done.md`
- 쓰기 경계: `docs/12-harness-boundaries.md`

상태 의미:

| 상태 | 의미 |
|---|---|
| 설계 확정 | 제품·기술 계약이 정해졌지만 구현 완료를 뜻하지 않음 |
| 설계 필요 | 제품 방향은 있으나 구현 전에 세부 계약을 확정해야 함 |
| 차단됨 | 외부 답변이나 선행 결정 전에는 진행하면 안 됨 |
| 구현 전 | 목표 계약은 있으나 코드가 없음 |
| 세부 계약 필요 | 큰 방향은 정했지만 구현 전 추가 결정을 해야 함 |
| 부분 구현 | 일부 코드·설정만 있고 완료 기준을 충족하지 않음 |
| 구현·검증 완료 | 코드와 적용 가능한 완료 기준을 검증함 |

## 2. 코드 기준선 (2026-09-11)

| 영역 | 상태 | 확인 근거 |
|---|---|---|
| Spring Boot | 기본 골격 | `TravelApplication`, `HelloController` 존재 |
| Java·Gradle | 설정 일부 존재 | Java 21, Spring Boot 4.1.1, Gradle wrapper |
| Web·Validation | dependency 존재 | webmvc·validation starter와 테스트 starter |
| JPA·MySQL·Flyway | 설정 초안만 존재 | `application.yml`에 datasource·JPA 초안이 있으나 dependency와 migration 없음 |
| Docker MySQL | 구성 파일 존재 | 실제 연결 성공 여부는 F0에서 확인 필요 |
| 인증·보안 | 구현 전 | User·Security·JWT 코드와 dependency 없음 |
| 지역 | 하네스만 존재 | `region/AGENTS.md`만 있고 Java 코드·`regions.json` 없음 |
| AI | 하네스만 존재 | Client·Service·DTO 코드 없음 |
| Place | 하네스만 존재 | Client·Service·DTO 코드 없음 |
| Route | 하네스만 존재 | 알고리즘·Client·Service 코드 없음 |
| Recommendation | 하네스만 존재 | Service·정책 코드 없음 |
| TravelPlan | 하네스만 존재 | Entity·Repository·Service·DTO 코드 없음 |
| DB migration | 구현 전 | `src/main/resources/db/migration`과 versioned 파일 없음 |
| 자동 테스트 | 기본 smoke | `TravelApplicationTests`만 존재 |
| 화면 | 정적 시안 | `static/Routy` HTML·CSS·JavaScript·미리보기 테스트, 새 API 미연동 |

`application.yml`의 JPA 설정만으로 JPA나 DB 연결이 구현된 것은 아니다. F0에서 dependency와 테스트 DB 방식을 설명하고 승인받은 뒤 설정의 유효성까지 함께 검증한다.

`AGENTS.md`는 구현 경계이며 기능 코드가 아니다. 도메인 하네스가 존재해도 해당 기능을 구현됨으로 표시하지 않는다.

## 3. 문서·하네스 정렬 결과

| 범위 | 상태 |
|---|---|
| `REMAKE.md` | 2026-09-11 결정과 카카오 답변 반영 |
| `README.md`, `docs/00-docs-index.md` | 국내·카카오 방향과 문서 역할 반영 |
| `docs/01~06` | 요구사항·아키텍처·DB·API·개발 계획·ADR 정렬 |
| `docs/07~10` | 준비도·테스트·운영·완료 판정 정렬 |
| `docs/11-command-roadmap.md` | D0~Q1 새 작업 ID와 선행 관계 반영 |
| `docs/12-harness-boundaries.md` | 새 단계별 쓰기 경계와 확정된 카카오 데이터 수명 규칙 반영 |
| 루트·하위 `AGENTS.md` | 도메인·문서·테스트·자원·화면 규칙 정렬 |
| `Routy/INTEGRATION.md` | 지역 계층·숙소·AI 메뉴·음식점 지도·API·데이터 수명 목표 계약 반영 |

D0 기준 문서, 보조 문서와 하네스의 교차 정렬을 완료했다. 이 상태는 설계 문서가 일치한다는 뜻이며 기능 구현 완료를 뜻하지 않는다.

## 4. 제품 결정 준비도

| 주제 | 설계 상태 | 구현 상태 | 근거 또는 다음 조치 |
|---|---|---|---|
| 국내 범위 | 확정 | 구현 전 | 서울·광역시·도는 상위 탐색, 최종 시·군·구 하나, 읍·면·동·해외 제외 |
| 지역 직접 검색·AI 추천 | 확정 | 구현 전 | 같은 `regions.json` 허용 목록 사용 |
| 이동수단 | 확정 | 구현 전 | 일정당 CAR 또는 PUBLIC_TRANSIT 하나 |
| 순수 경로 | 확정 | 구현 전 | Haversine, Nearest Neighbor, 2-opt |
| 실제 경로 호출 시점 | 확정 | 구현 전 | 최종 후보의 인접 구간만 조회 |
| 이동 시간 | 확정 | 구현 전 | 제공자 예상 초를 10분 단위로 올림 |
| 체류 시간 | 확정 | 구현 전 | 유형 미노출, 30~480분의 10분 단위 |
| 사용자 장소 이름 | 확정 | 구현 전 | 빈 입력에서 직접 작성, trim 후 1~50자 |
| 시간 초과 | 확정 | 구현 전 | 저장 차단, 자동 삭제·체류 축소 없음 |
| 완료 후 편집·조회·공유 | 확정 | 구현 전 | 제한 텍스트 편집, 외부 호출·지도 없는 조회 |
| 호출 한도·저장소 | 확정 | 구현 전 | MySQL 공유 카운터·requestId 상태, 경로 쿼터 사전 확보 |
| 이동시간 출처 | 확정 | 구현 전 | 숫자만 저장, 생성 warning 비영속, 집계 metric |
| 카카오 좌표 활용 계약 | 정책 확인 완료 | 구현 전 | 2026-09-11 DevTalk 답변과 ADR-028 |
| 운영 배포 | 차단 | 구현 전 | 기능·테스트·운영 검증 필요 |

카카오 답변 반영은 완료됐다. 이후 모든 좌표 기반 구현은 ADR-028의 일시 사용·즉시 폐기 조건을 따라야 한다.

## 5. 구현 전 확정할 세부 계약

| 항목 | 로드맵 시점 | 기록 위치 |
|---|---|---|
| 테스트 DB 방식과 JPA·MySQL·Flyway dependency | F0-02 | 개발 기반 Change Envelope와 운영·테스트 문서 |
| JWT 만료·재발급·로그아웃, User 삭제 | U1-01 | API·DB·보안 ADR·운영 |
| 지역 공공데이터 출처·기준일·생성 절차 | G1-01 | 데이터 ADR·운영 |
| CAR·PUBLIC_TRANSIT 초기 추정 계수 | R1-01 | ADR·테스트 |
| OpenAI 모델·전체 예산 | G1-06 전 | 운영·AI 계약 |
| selectionToken 서명·만료·키 교체 | P1-02 | 보안 ADR·API·운영 |
| 자동차 요청 단위·공식 쿼터 | R2-01 | API·운영 |
| AI 메뉴·식사·음식점 흐름 | 확정 | 메뉴 1~5개 사용자 확정, 식사 60분·한쪽 여유 15분, estimate 후 지도 선택, 저장 후 재검색 없음 |
| 공유 토큰 해시·만료 | T1-01 | DB·API·보안 ADR |
| requestId 저장 성공 후 재요청 응답 | U1-05 또는 T1-05 전 | API·ADR·테스트 |

미확정 값을 임시 상수, 넓은 nullable, 가짜 운영 데이터나 테스트 생략으로 우회하지 않는다.

## 6. 문서 정렬 후 진행 가능한 범위

카카오 답변과 무관하게 다음 순서로 진행할 수 있다.

1. `F0-01`: 실제 코드·설정·dependency 실행 가능성 감사
2. `F0-02~05`: 개발·DB·오류 처리 기반
3. `U1`: 인증과 사용자별 한도 기반
4. `G1`: 국내 지역 기준과 지역 AI
5. `R1`: 순수 거리·방문 순서 알고리즘
6. `C1`: 외부 Client 인터페이스와 Fake

`K0-02A`가 완료됐으므로 이후 구현 요청이 있을 때 `P1 → R2 → S1 → T1 → W1 → Q1` 순서를 따른다.

## 7. 현재 작업 상태

현재 활성 작업은 없다. D0 문서 정렬과 전체 Markdown 감사는 완료됐으며 실제 기능은 구현하지 않았다.

F0-01에 필요한 코드 기준선은 문서 작업 중 읽기 전용으로 확인했지만 정식 작업 완료로 표시하지 않는다. 사용자가 개발 시작을 별도로 요청할 때만 F0 이후 작업을 진행한다.

## 8. 완료 해석

- Accepted ADR은 구현 완료가 아니다.
- 목표 DB·API 문서가 존재해도 migration이나 endpoint가 구현됐다는 뜻은 아니다.
- 하네스 파일과 Fake Client는 실제 도메인 기능 또는 제공자 정책 검증을 대신하지 않는다.
- 카카오 임시 사용 조건 확인만으로 좌표 기반 제작 흐름을 완료로 표시하지 않는다. 구현과 테스트가 필요하다.
- 구현 완료는 관련 테스트, 전체 테스트와 `docs/10-definition-of-done.md`를 확인한 뒤에만 기록한다.
- 운영 가능은 구현 완료에 더해 비밀값, 로그, 호출 한도, health, smoke와 확정된 카카오 데이터 수명 계약을 충족해야 한다.
