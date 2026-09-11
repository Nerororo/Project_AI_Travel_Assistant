# Codex 대화형 구현 로드맵

## 1. 역할과 사용법

이 문서는 Routy의 실제 작업 순서와 작업 ID를 관리한다. 큰 개발 방향은 `docs/05-development-plan.md`, 현재 구현 상태는 `docs/07-implementation-readiness.md`, 경로별 수정 범위는 `docs/12-harness-boundaries.md`를 따른다.

한 번에 작업 ID 하나만 진행한다. 구현 전에는 적용되는 문서를 읽고 다음 Change Envelope를 제시한다.

```text
Task ID:
Allowed Paths:
Conditional Paths:
Forbidden Paths:
Verification:
```

- 표의 요청 문구는 예시이며 Change Envelope를 넓히지 않는다.
- 명시되지 않은 파일이 필요하면 수정 전에 이유와 영향을 설명하고 승인을 받는다.
- 새 dependency는 목적과 대안을 먼저 설명한다.
- 구현 후 관련 테스트, `./gradlew test`, `git diff --check`와 실제 변경 경로를 확인한다.
- 완료 판정은 `docs/10-definition-of-done.md`를 따른다.
- 사용자가 요청하지 않으면 git add, commit, stash, reset을 실행하지 않는다.

## 2. 전체 순서

```text
D0 문서·하네스 정렬
→ F0 개발 기반
→ U1 인증·소유권
→ G1 국내 지역 기준
→ R1 순수 거리·경로
→ C1 외부 Client 계약과 Fake
→ K0 카카오 답변 반영 완료
→ P1 장소 → R2 독립 경로 기반 → S1 추정 일정·추천
→ T1 외부 경로 통합·완료 일정 저장·API → W1 화면·공유 → Q1 운영
```

인증은 사용자별 호출 한도와 일정 소유권의 선행 조건이다. K0-02A는 2026-09-11 완료됐다. 이후 좌표 기반 장소 제작은 확정된 일시 사용·즉시 폐기 계약을 구현과 테스트에서 지켜야 한다.

## 3. D0 — 문서와 하네스 정렬

| ID | 상태 | 작업 | 완료 확인 |
|---|---|---|---|
| D0-01 | 완료 | 요구사항·아키텍처·DB·API·개발 계획·ADR·테스트·운영·DoD 정렬 | 국내·카카오·두 단계 생성·저장 계약이 일치 |
| D0-02 | 완료 | `docs/12-harness-boundaries.md` 교체 | 새 단계별 Allowed·Conditional·Forbidden Paths 확정 |
| D0-03 | 완료 | 문서 index, README, UI 연동 문서 정렬 | 구형 Google·해외 흐름과 잘못된 문서 링크 제거 |
| D0-04 | 완료 | 루트와 하위 `AGENTS.md` 정렬 | 새 도메인 책임·필수 참조·경계를 코드 위치별 반영 |
| D0-05 | 완료 | `docs/07-implementation-readiness.md` 최종 감사 | 문서 상태와 실제 코드 기준선이 정확함 |

현재 활성 작업은 없다. D0 문서 정렬은 완료됐고 실제 기능은 구현하지 않았다. 다음 구현 작업도 새 작업 ID와 Change Envelope를 정한다.

## 4. F0 — 개발 기반

| ID | 작업 | 완료 확인 |
|---|---|---|
| F0-01 | 현재 코드·설정·dependency 실행 가능성 감사 | 구현·미구현과 설정 불일치 설명 |
| F0-02 | JPA·MySQL·Flyway·DB 테스트 방식 설계 | dependency 이유와 Testcontainers 또는 전용 DB 선택 |
| F0-03 | 승인된 dependency와 profile·migration 테스트 기반 구현 | 컴파일, 빈 DB migration, `ddl-auto: validate` 준비 |
| F0-04 | 공통 오류 응답·Exception Handler 설계·구현 | validation·인증·not found·422·429·503 계약 테스트 |
| F0-05 | 기반 회귀 점검 | 전체 테스트와 운영 설정의 비밀값 부재 확인 |

요청 예시: `F0-02를 설명만 해줘. dependency 선택지와 수정 파일, 테스트 DB 방식을 초보자 기준으로 설명해줘.`

## 5. U1 — 인증과 소유권

| ID | 작업 | 완료 확인 |
|---|---|---|
| U1-01 | 비밀번호·JWT·공개 endpoint·User 삭제 정책 설계 | 미확정 보안 계약 ADR 확정 |
| U1-02 | User Entity·Repository·migration | 이메일 UNIQUE와 비밀번호 비노출 통합 테스트 |
| U1-03 | 회원가입 Service·API | 해시 저장, 중복 이메일, validation |
| U1-04 | Spring Security·JWT 로그인 | 성공·실패·만료·변조 토큰 테스트 |
| U1-05 | MySQL 호출 한도와 `requestId` 상태 저장소 기반 | 사용자·서비스 분·일 카운터, 원자 확보, 서울 자정, 10분 상태·payload 미저장 |
| U1-06 | 인증·보안 회귀 점검 | 공개 API 외 인증 강제와 공통 DoD 통과 |

TravelPlan 소유권 연결은 T1에서 Aggregate와 함께 완성한다. U1에서는 인증 사용자 ID를 안전하게 전달하는 공개 계약까지만 만든다.

## 6. G1 — 국내 지역 기준

| ID | 작업 | 완료 확인 |
|---|---|---|
| G1-01 | 공공 행정구역 데이터 출처·기준일·생성 절차 ADR | 출처와 갱신 책임 확정 |
| G1-02 | `regions.json` 구축 | 상위 서울·광역시·도와 최종 시·군·구, 읍·면·동·해외 제외 |
| G1-03 | Region 값 객체·Loader·시작 검증 | ID·상위 관계·이름·대표 좌표 오류 테스트 |
| G1-04 | 지역 직접 검색 Service·API | 결정적 검색·정렬과 DTO·HTTP 테스트 |
| G1-05 | AI 지역 추천 Client 계약·Fake·Service | 허용 목록에서 정확히 3개, 계약 위반 테스트 |
| G1-06 | OpenAI 실제 Client 설계·구현 | 구조화 응답·timeout·키·비용, 자동 테스트는 Fake |
| G1-07 | 지역 AI API와 한도 연결 | 2회/분·10회/일과 오류 계약 |
| G1-08 | 지역 단계 DoD 점검 | 데이터 출처, 직접 검색, AI와 전체 테스트 통과 |

## 7. R1 — 순수 거리와 방문 순서

| ID | 작업 | 완료 확인 |
|---|---|---|
| R1-01 | Coordinate·Haversine·기하 중앙값·메도이드·추정 계수 ADR 설계 | 단위·범위·수렴·동률·CAR/PUBLIC_TRANSIT 계수 확정 |
| R1-02 | Haversine 구현·단위 테스트 | 0 거리·대칭성·허용 오차 |
| R1-03 | Nearest Neighbor와 동률 규칙 설계 | 입력·출력·시작점·복잡도 이해 |
| R1-04 | Nearest Neighbor 구현·테스트 | 모든 장소 정확히 한 번, 결정성 |
| R1-05 | 2-opt 종료 조건 설계·구현 | 경로 비악화·집합 보존·반복 상한 |
| R1-06 | 이동 시간 추정과 10분 단위 정책 | 이동수단 분리, 경계값 테스트 |
| R1-07 | 순수 경로 성능·회귀 점검 | 최대 MVP 입력 측정과 전체 테스트 |

이 단계에는 Controller, Repository와 외부 HTTP Client를 추가하지 않는다.

## 8. C1 — 외부 Client 계약과 Fake

| ID | 작업 | 완료 확인 |
|---|---|---|
| C1-01 | Place Client 요청·응답·오류 계약 설계 | 외부 필드와 저장 모델 분리 |
| C1-02 | Fake Place Client와 PlaceService 골격 | 성공·빈 결과·timeout·4xx·5xx 테스트 |
| C1-03 | CAR/PUBLIC_TRANSIT Route Client 계약 설계 | 인접 구간, 정상 경로 없음, 기술 장애 구분 |
| C1-04 | Fake Route Client와 RouteService 골격 | 이동수단 분기·재시도·fallback 테스트 |
| C1-05 | 메뉴 AiClient 계약·Fake·Service | 지역·관광지 맥락의 메뉴 1~5개·검색어·이유·대상 생성, 사용자 확정·오류·한도 테스트 |
| C1-06 | 실제 Client 구현 전 공식 문서 감사 | endpoint·인증·쿼터·요금·정책 기록 |

실제 카카오 HTTP 호출과 좌표를 받는 공개 endpoint는 완료된 K0-02A를 선행 조건으로 P1·R2에서 구현한다.

## 9. K0 — 카카오 답변 반영

| ID | 작업 | 완료 확인 |
|---|---|---|
| K0-01 | 카카오 담당자 답변 검토 | 완료 — 일시 저장·참조 후 즉시 폐기 허용 확인 |
| K0-02A | 허용 답변의 ADR·문서 반영 | 완료 — ADR-028 Accepted, API·테스트·운영 조건 반영 |

K0-02A는 완료됐다. P1 이후 작업은 ADR-028의 좌표 수명과 저장 금지 조건을 계속 적용하며, 정책 확인을 기능 구현 완료로 간주하지 않는다.

## 10. P1 — 카카오 장소 제작 흐름

선행 조건: K0-02A 완료.

| ID | 작업 | 완료 확인 |
|---|---|---|
| P1-01 | 장소 역할·반경·카테고리→체류 시간 정책 구현 | 유형 미노출, 기본 시간과 10분 조정 테스트 |
| P1-02 | `selectionToken` 보안 ADR | 서명·만료·키 교체·payload 최소화 확정 |
| P1-03 | token 발급·검증 구현 | 만료·변조·역할 불일치 테스트, 서버 캐시 없음 |
| P1-04 | Kakao Local 실제 Client 구현 | 키·timeout·오류 변환, Fake 기반 자동 테스트 |
| P1-05 | 장소 검색 API와 사용자 한도 연결 | 20회/분·300회/일, 임의 후보 생성 없음 |
| P1-06 | 장소 선택 브라우저 메모리 흐름 | 저장소 미사용, 빈 표시 이름, 종료 시 폐기 |
| P1-07 | 저장 금지·정책 회귀 점검 | 좌표·주소·카테고리·장소명·원문 미저장 |

## 11. R2 — 독립 경로 기반

선행 조건: K0-02A와 C1 완료.

이 단계는 여행 일정이나 날짜별 배치를 알지 못한다. 정렬된 인접 좌표 구간 목록을 받아 제공자별 예상 이동시간을 반환하는 독립 기능만 구현한다.

| ID | 작업 | 완료 확인 |
|---|---|---|
| R2-01 | 자동차 Client 공식 계약 확정 | endpoint·쿼터·요금·구간 요청 방식 |
| R2-02 | 카카오모빌리티 Client 구현 | 실제 응답 매핑, 60회/분·120회/일 |
| R2-03 | 카카오맵 대중교통 Client 계약 구현 | REST endpoint·인증·60회/분·120회/일·900건 차단 |
| R2-04 | 카카오맵 대중교통 Client 구현 | 이동수단 분리와 Fake 계약 테스트 |
| R2-05 | 정렬된 인접 구간 검증 계약 | 좌표 구간 목록을 받아 예상 이동시간을 10분 단위로 반환, 고정 buffer 없음 |
| R2-06 | 재시도·fallback·경로 없음 통합 | 기술 장애 1회 재시도, 정상 없음은 422 |
| R2-07 | 쿼터 사전 확보·전체 fallback과 관측 | 부족 시 외부 호출 0건, 전체 Haversine 예상시간·warning·metric |

## 12. S1 — 추정 일정 계산과 추천

이 단계는 Haversine 추정만 사용하며 R2의 실제 외부 Client나 경로 검증 Service를 호출하지 않는다.

| ID | 작업 | 완료 확인 |
|---|---|---|
| S1-01 | 날짜·활동 시간·체류·식사 입력 정책 | 기간 1~7일, 식사 60분, 점심·저녁 시간대, 한쪽 이동 여유 기본 15분 |
| S1-02 | 날짜별 배치와 시간 예산 설계 | 결정적 동률·선택 장소 전체 보존·시간 초과 규칙 |
| S1-03 | 추정 일정 Service | Haversine 기반, DB·실제 Route Client 미호출 |
| S1-04 | `POST /travel-plans/estimate` | `routeVerified=false`, 입력·오류 계약 |
| S1-05 | 숙소 지도 탐색 중심 계산 | 기하 중앙값 5·10km, 메도이드, 현재 지도 영역 |
| S1-06 | 음식점 지도 탐색과 추천 점수 | 시간 적합성·식사 전후 Haversine 이탈, 목록·마커 연동, 현재 지도 재검색 |
| S1-07 | 일정 계산 DoD 점검 | 자동·사용자 배치, 시간 초과 시 자동 삭제·축소 없음 |

이 단계는 계산 결과까지만 만들며 DB에 작성 초안이나 완료 일정을 저장하지 않는다.

## 13. T1 — 완료 일정 저장과 API

| ID | 작업 | 완료 확인 |
|---|---|---|
| T1-01 | Meal 연결·User 삭제·공유 토큰 ADR | 미선택 MEAL null, 저장 전 선택 식당만 연결, 나머지 DB·API 계약 확정 |
| T1-02 | TravelPlan Aggregate와 migration 설계 | TravelPlan·Day·Item·PlanPlace 관계와 제약 |
| T1-03 | Entity·Repository·migration 구현 | UNIQUE·CHECK·FK와 빈 DB 적용 테스트 |
| T1-04 | 외부 경로 반영 완료 계산 | 사용자 날짜·순서 보존, 인접 구간 예상 이동시간과 종료 시각 재검증 |
| T1-05 | 계산 완료 결과 저장 Service | 외부 호출 후 짧은 트랜잭션, 전체 rollback |
| T1-06 | `POST /travel-plans` 연결 | 서버 재계산·201·422·503·warning |
| T1-07 | 목록·상세 API와 소유권 | 저장 DTO만 조회, 외부 Client 미호출 |
| T1-08 | 제한된 PATCH와 DELETE | 제목·사용자 이름·메모만 수정, Aggregate 삭제 |
| T1-09 | 공유 토큰과 읽기 전용 API | 비공개 필드 미노출, 외부 Client 미호출 |
| T1-10 | 저장 금지·Aggregate DoD 점검 | 금지 열·값 부재, migration·rollback·전체 테스트 |

## 14. W1 — 실제 화면

| ID | 선행 | 작업 | 완료 확인 |
|---|---|---|---|
| W1-01 | G1 | 회원·지역 직접 검색·AI 추천 연결 | 인증과 국내 허용 목록 |
| W1-02 | P1 | 이동수단·장소 선택·체류 조정 | 유형 미노출, 빈 이름, 메모리 수명 |
| W1-03 | S1 | 추정 일정과 오류 복구 | 입력 유지, 중복 제출 방지 |
| W1-04 | T1 | 완료 일정 고정 HTML | 지도 없이 저장 시간표·이름·외부 링크 |
| W1-05 | T1 | 내 일정·제한 편집·공유 | 소유권과 읽기 전용 |
| W1-06 | 전체 | 반응형·키보드·브라우저 회귀 | 핵심 성공·실패 흐름 통과 |

화면 작업은 `src/main/resources/static/Routy/**`와 직접 대응하는 테스트로 제한한다. 원본 템플릿과 백엔드 계약은 화면 작업에서 임의로 바꾸지 않는다.

## 15. Q1 — 품질과 운영

| ID | 작업 | 완료 확인 |
|---|---|---|
| Q1-01 | 공통 오류·로그·민감 정보 감사 | 원문·좌표·비밀값 부재 |
| Q1-02 | local/test/prod/smoke profile 정리 | Fake 기본, 실제 호출 분리 |
| Q1-03 | health·관측 설계와 필요한 dependency 승인 | 앱·DB·외부 dependency 구분 |
| Q1-04 | 승인된 health·metric 구현 | 호출량·latency·retry·fallback·차단 관찰 |
| Q1-05 | migration·Docker·배포 검증 | backup·restore·schema validate |
| Q1-06 | 실제 제공자 smoke | 제한 키·예산, 자동 테스트와 분리 |
| Q1-07 | 전체 DoD와 준비도 갱신 | 코드·테스트·문서·운영 상태 일치 |

## 16. 작업 종료 보고

각 작업의 완료 보고에는 다음을 포함한다.

1. 변경 결과와 수정 파일
2. Change Envelope와 범위 밖 변경 여부
3. 관련 테스트와 전체 테스트 결과
4. `git diff --check` 결과
5. 확인한 DoD 항목
6. 남은 미확정 사항과 외부 제공자 재확인 항목
7. 처음 사용한 Spring 개념 설명

## 17. 지금 시작할 작업

현재 활성 작업은 없다. K0 답변 반영과 완료 표시된 D0 작업만 완료 상태이며 실제 기능은 구현하지 않았다. 다음 작업을 시작할 때 로드맵과 현재 Git 상태를 다시 확인해 하나의 작업 ID와 Change Envelope를 정한다.
