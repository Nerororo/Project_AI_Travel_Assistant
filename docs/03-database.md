# 데이터베이스 설계

## 1. 목적과 상태

이 문서는 확정된 완료 일정의 영속 저장 계약을 정의한다. 아직 Entity나 migration이 구현됐다는 뜻은 아니다.

Routy는 카카오 장소 데이터베이스를 복제하지 않는다. 카카오 장소 ID와 장소 URL, 사용자가 작성한 정보, Routy가 계산한 일정 정보만 저장한다. 제작 중 검색 결과와 좌표는 영속 저장 모델에 포함하지 않는다.

2026-09-11 카카오 DevTalk 답변으로 카카오 좌표를 한 번의 제작 흐름에서 일시적으로 사용하고 즉시 폐기하는 방식이 허용됨을 확인했다. 좌표와 검색 응답은 DB 저장 대상이 아니다.

---

## 2. Aggregate와 관계

~~~text
User
 └─ 1:N TravelPlan
          ├─ 1:N FoodPreference
          ├─ 1:N TravelPlanDay
          │        └─ 1:N TravelPlanItem
          └─ 1:N PlanPlace
~~~

- TravelPlan이 일정 Aggregate Root다.
- TravelPlan 삭제 시 FoodPreference, Day, Item, PlanPlace를 함께 삭제한다.
- User는 여러 TravelPlan을 소유한다.
- PlanPlace는 해당 일정 안에서만 쓰는 장소 참조다.
- 다른 일정의 PlanPlace를 공유하지 않는다. 사용자 작성 표시 이름과 메모가 일정마다 다를 수 있기 때문이다.
- 외부 API 호출과 일정 계산을 끝낸 뒤 하나의 트랜잭션으로 Aggregate 전체를 저장한다.
- 제작 중 초안은 DB에 저장하지 않는다.

---

## 3. users

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| email | VARCHAR(255) | N | UNIQUE, 정규화된 로그인 이메일 |
| password_hash | VARCHAR(255) | N | 평문 저장 금지 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

닉네임은 현재 필수 요구사항이 아니므로 초기 스키마에 넣지 않는다. 계정 삭제와 일정 처리 방식은 인증 설계 ADR에서 확정한다.

---

## 4. travel_plans

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| user_id | BIGINT | N | User FK |
| title | VARCHAR(100) | N | 사용자 작성 일정 제목 |
| region_id | VARCHAR(100) | N | regions.json의 내부 식별자 |
| region_display_name | VARCHAR(100) | N | 생성 당시 표준 지역 이름 snapshot |
| start_date | DATE | N | 여행 시작일 |
| end_date | DATE | N | 여행 종료일 |
| travel_mode | VARCHAR(20) | N | CAR 또는 PUBLIC_TRANSIT |
| meal_travel_buffer_minutes | INT | N | 일반 식사 한쪽 이동 여유, 기본 15분 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

필수 제약:

- start_date는 end_date보다 늦을 수 없다.
- 여행 기간은 Service에서 1~7일로 검증한다.
- travel_mode는 CAR, PUBLIC_TRANSIT만 허용한다.
- meal_travel_buffer_minutes는 0~60 정수다.
- 완료 후 title만 수정할 수 있다.
- region_id는 외부 제공자 ID가 아니며 자체 정적 기준 데이터의 키다.
- 과거 일정 표시가 지역 목록 갱신에 영향받지 않도록 region_display_name을 함께 저장한다.

TravelPlan은 완료된 일정만 표현한다. DRAFT 상태나 제작 중 좌표를 저장하기 위한 컬럼을 추가하지 않는다.

확정한 메뉴명은 일정별 중복 없는 최대 5개 `food_preferences` 행으로 저장한다. 각 `food_name`은 trim 후 1~50자이며 AI 요청·응답 원문, 추천 이유, 검색어와 대상 관광지 연결은 저장하지 않는다.

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | TravelPlan FK |
| food_name | VARCHAR(50) | N | 사용자가 확정한 메뉴명 |

`(travel_plan_id, food_name)`은 UNIQUE이며 TravelPlan 삭제 시 함께 삭제한다.

---

## 5. plan_places

한 일정에서 사용하는 장소의 최소 참조와 사용자 작성 정보를 저장한다.

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | TravelPlan FK |
| kakao_place_id | VARCHAR(255) | N | 카카오 장소 ID |
| place_url | VARCHAR(1000) | N | 허용된 카카오맵 URL |
| role | VARCHAR(20) | N | ATTRACTION, HOTEL, RESTAURANT |
| display_name | VARCHAR(50) | N | 사용자가 빈 입력창에 직접 작성한 이름 |
| memo | VARCHAR(1000) | Y | 사용자 메모 |
| stay_minutes | INT | Y | 관광지 확정 체류시간 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

규칙:

- display_name은 카카오 장소명의 복사 컬럼이 아니다.
- display_name은 trim 후 1~50자다.
- place_url은 허용 도메인과 형식을 Service에서 검증한다.
- stay_minutes는 ATTRACTION에서만 필수이며 30~480, 10의 배수다.
- HOTEL과 RESTAURANT의 stay_minutes는 null이다.
- 카카오 카테고리와 내부 체류 유형은 저장하지 않는다.
- 완료 후 display_name과 memo만 수정할 수 있다.
- 같은 kakao_place_id가 역할 또는 날짜에 따라 합법적으로 다시 필요할 수 있으므로 단순 전역 UNIQUE를 두지 않는다. 일정 안의 중복 허용 규칙은 API·Service 계약에서 검증한다.

숙소가 없는 당일치기는 HOTEL 역할 행이 없다. 1박 이상 일정에는 Service가 HOTEL 역할 한 개를 보장한다.

---

## 6. travel_plan_days

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | TravelPlan FK |
| day_number | INT | N | 1부터 시작하는 일차 |
| travel_date | DATE | N | 실제 날짜 |
| activity_start_time | TIME | N | 해당 날짜 활동 시작 |
| activity_end_time | TIME | N | 해당 날짜 활동 종료 |

필수 제약:

- (travel_plan_id, day_number) UNIQUE
- (travel_plan_id, travel_date) UNIQUE
- activity_start_time < activity_end_time
- day_number와 날짜의 연속성, 여행 기간 포함 여부는 Service에서 검증한다.

첫날 도착 시각과 마지막 날 출발 시각은 계산이 끝난 activity 범위로 저장한다. 원래 외부 교통편 정보는 저장 대상이 아니다.

---

## 7. travel_plan_items

완료 화면의 날짜별 시간표 행을 저장한다.

| Column | Type 예시 | Nullable | 제약·설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_day_id | BIGINT | N | Day FK |
| item_order | INT | N | 화면 표시 및 실행 순서 |
| item_type | VARCHAR(20) | N | VISIT, STAY, MEAL, MOVE |
| plan_place_id | BIGINT | Y | 장소가 있는 행의 PlanPlace FK |
| start_time | TIME | N | 예상 시작 시각 |
| end_time | TIME | N | 예상 종료 시각 |
| estimated_minutes | INT | Y | MOVE 행의 10분 단위 예상 이동시간 |

항목별 규칙:

| item_type | plan_place_id | estimated_minutes | 의미 |
|---|---:|---:|---|
| VISIT | 필수, ATTRACTION | null | 관광지 체류 |
| STAY | 필수, HOTEL | null | 숙소 출발·도착 표시 |
| MEAL | 선택, RESTAURANT | null | 식사 60분. 식당 미선택 상태도 가능 |
| MOVE | null | 필수 | 인접 항목 사이 예상 이동 |

공통 제약:

- (travel_plan_day_id, item_order) UNIQUE
- start_time < end_time
- MOVE의 estimated_minutes는 양수이며 10의 배수다.
- MOVE는 end_time - start_time과 estimated_minutes가 일치해야 한다.
- MOVE에는 API 결과와 Haversine fallback을 구분하는 출처 enum이나 상태 컬럼을 두지 않는다. 생성 당시 warning도 영속 저장하지 않는다.
- MEAL은 60분이며 점심 11:30~14:00, 저녁 17:30~20:30과 하루 활동 범위를 Service에서 검증한다.
- 날짜의 모든 Item은 서로 겹치지 않고 item_order 순서와 시간이 일치해야 한다.
- Day 활동 범위를 벗어나는 Item을 저장하지 않는다.
- 하루 VISIT 수는 최대 5개다.
- 실제 경로 원본 시간은 저장하지 않고 10분 단위로 올린 예상시간만 MOVE에 저장한다.

식당을 최종 선택하지 않은 MEAL은 plan_place_id가 null일 수 있다. 저장 전에 선택한 식당만 RESTAURANT PlanPlace와 연결하며 완료 후 음식점 검색·추가·교체는 제공하지 않는다.

---

## 8. api_usage_counters와 request_executions

호출 제한은 여러 사용자와 서버 인스턴스가 같은 값을 보도록 MySQL에 저장한다.

`api_usage_counters`는 `scope_type`, `scope_id`, `feature`, `window_type`, `window_start` 조합을 UNIQUE로 두고 `used_count`, `expires_at`을 저장한다. USER 범위와 SERVICE 전체 범위를 분리하며 조건부 UPDATE와 짧은 트랜잭션으로 필요한 호출 수를 외부 API 호출 전에 원자적으로 확보한다.

`request_executions`는 `user_id`, `feature`, `request_id` 조합을 UNIQUE로 두고 처리 상태와 `expires_at`만 저장한다. 처리 중 상태의 동일 요청은 `REQUEST_IN_PROGRESS`, 성공 상태의 동일 요청은 `REQUEST_ALREADY_COMPLETED`로 차단하며 외부 호출·저장 로직을 반복하지 않는다. 보관 시간은 10분이며 결과 리소스 ID, payload·response·좌표·외부 원문은 저장하지 않는다.

만료 행은 묶어서 정리하되 모든 판정은 `expires_at`을 확인하므로 삭제 지연이 현재 한도에 영향을 주지 않아야 한다. 외부 호출 전에 카운터 확보 트랜잭션을 끝내며 실제 외부 호출을 DB 트랜잭션 안에서 수행하지 않는다.

---

## 9. 저장하지 않는 데이터

다음 값은 어떤 Entity나 컬럼에도 저장하지 않는다.

- 카카오 장소명
- 주소와 좌표
- 전화번호와 카테고리
- 카카오 검색어와 요청·응답 원문
- 자동차·대중교통 경로 요청·응답 원문
- 경로 좌표, polyline, 지도 이미지
- 제공자가 반환한 원본 초·분 소요시간
- 카카오 검색 결과 캐시
- 내부 체류 유형
- 제작 중 추천 후보 목록과 점수
- AI 요청·응답 원문
- 중복 요청의 payload와 response

같은 제한을 DB뿐 아니라 Redis, Caffeine, 서버 세션, 파일, 로그, fixture, localStorage, sessionStorage, IndexedDB에도 적용한다.

중복 요청 방지용 `request_executions`에는 userId, 기능, requestId, 처리 상태, 만료 시각만 최대 10분 유지한다. 결과 리소스 ID, 외부 요청·응답과 좌표는 포함하지 않는다.

---

## 10. 완료 후 변경

DB를 변경할 수 있는 완료 후 작업은 다음뿐이다.

- travel_plans.title
- plan_places.display_name
- plan_places.memo
- 일정 전체 삭제

다음 변경 API는 제공하지 않는다.

- region_id와 여행 날짜
- travel_mode
- Day의 활동 범위
- Item의 날짜·순서·시각
- 관광지 stay_minutes
- 장소 추가·삭제·교체
- 경로 재계산

동선을 바꾸려면 기존 일정을 보존하고 새 TravelPlan Aggregate를 생성한다.

---

## 11. 인덱스

초기 후보:

~~~text
users(email) UNIQUE
travel_plans(user_id, start_date)
travel_plan_days(travel_plan_id, day_number) UNIQUE
plan_places(travel_plan_id, role)
travel_plan_items(travel_plan_day_id, item_order) UNIQUE
~~~

카카오 장소 ID에는 전역 UNIQUE를 두지 않는다. 실제 조회 패턴과 실행 계획 없이 추가 인덱스를 만들지 않는다.

---

## 12. 무결성과 삭제

- TravelPlan 저장은 PlanPlace, Day, Item 전체가 성공하거나 전체가 rollback되어야 한다.
- TravelPlan 삭제 시 하위 PlanPlace, Day, Item을 함께 삭제한다.
- User FK와 모든 Aggregate FK를 DB 제약으로 보장한다.
- Day를 삭제하면 해당 Item을 함께 삭제한다.
- 참조 중인 PlanPlace를 개별 삭제하는 완료 후 기능은 제공하지 않는다.
- URL 도메인, 역할별 nullable 조합, 일정 기간, 시간표 겹침처럼 DB CHECK로 표현하기 복잡한 규칙은 Service에서 검증하고 테스트한다.
- DB CHECK로 표현 가능한 enum, 양수, 시간 순서, 10분 배수는 migration에 반영한다.

---

## 13. Migration 원칙

- 첫 스키마부터 Flyway versioned migration으로 관리한다.
- 적용된 migration을 수정하지 않고 새 버전을 추가한다.
- 실제 현재 migration 상태를 확인한 뒤 다음 버전 번호를 정한다.
- Hibernate는 schema를 생성·변경하지 않고 모든 profile에서 validate한다.
- migration 변경은 별도 구현 작업과 Change Envelope에서 수행한다.
- 파괴적 변경에는 기존 데이터 처리, 백업, 호환 기간, 복구 방법이 필요하다.

F0-02에서 DB 통합 테스트는 운영과 같은 MySQL 8.4 이미지를 사용하는 Testcontainers로 결정했다. H2처럼 SQL dialect와 제약 동작이 다른 대체 DB는 migration·Repository 검증에 사용하지 않는다. 각 검증은 Flyway가 빈 schema에 production migration을 먼저 적용하고, 그 다음 Hibernate `ddl-auto: validate`가 Entity와 schema의 일치를 확인해야 한다. migration 성공과 JPA validate 성공은 서로 다른 검증 결과로 구분한다.

이 문서 교체만으로 Entity나 migration이 구현된 것으로 간주하지 않는다.

---

## 14. JPA 원칙

- TravelPlan을 Aggregate Root로 둔다.
- Aggregate 내부 생명주기가 같은 Day, Item, PlanPlace에만 필요한 cascade와 orphanRemoval을 적용한다.
- 모든 관계를 양방향으로 만들지 않는다.
- Collection은 Lazy Loading을 기본으로 한다.
- 조회 API는 Entity를 직접 반환하지 않고 필요한 Response DTO로 변환한다.
- 외부 API 호출 중 DB 트랜잭션을 열지 않는다.
- 외부 검증과 계산이 끝난 뒤 짧은 저장 트랜잭션을 시작한다.

---

## 15. 미확정 구현 세부사항

다음은 제품 정책이 아니라 구현 전에 API·ADR·migration 단계에서 확정할 세부사항이다.

- 실제 테이블·컬럼 이름과 VARCHAR 길이의 DB별 최종 값
- User 삭제와 공유 토큰의 저장 방식
- 공유 토큰의 테이블 분리, 해시 방식과 만료 정책
- User 삭제 시 일정 보존·삭제 정책

미확정 사항을 임시 컬럼이나 nullable 완화로 우회하지 않는다.
