# 데이터베이스 설계

## 1. 기본 원칙

초기 MVP에서는 전 세계 장소 정보를 자체 DB에 복제하지 않는다. Google Places를 장소 원천으로 사용하고, DB에는 Google Place ID와 Routy가 직접 생성한 여행 계획 데이터만 영구 저장한다.

`Place` Entity는 Google 장소 본문을 수정하는 CRUD 대상이 아니라, TravelPlan의 FK 연결을 위한 내부 참조다. 장소명·주소·좌표·사진·Google 장소 유형은 필요한 시점에 Google Places에서 조회해 DTO로 사용한다.

---

## 2. 전체 관계

```text
User
 │
 │ 1:N
 ▼
TravelPlan
 │
 ├──────── 1:N ──────── FoodPreference
 │
 └──────── 1:N
             ▼
       TravelPlanDay
             ├──────── 1:N ──────── TravelPlanMealSlot
             │
             │ 1:N
             ▼
      TravelPlanPlace
             │
             │ N:1
             ▼
           Place
```

회원 기능 구현 전에는 `TravelPlan.user_id`와 User 연관관계를 만들지 않는다. U1 인증 단계에서 `users` 테이블을 먼저 만든 뒤 새 migration으로 `travel_plans.user_id`와 FK를 추가한다.

초기 학습 단계에서는 회원 기능 때문에 핵심 여행 로직 개발이 막히지 않도록 하며, 인증 도입 시 기존 개발 데이터의 소유권 이관 방법을 U1 설계에서 함께 결정한다.

---

## 3. places

Google 장소를 내부 Aggregate에서 참조하기 위한 테이블.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| google_place_id | VARCHAR(255) | N | Google Places의 장소 식별자, UNIQUE |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

Google Place ID는 장기 저장 가능한 식별자로 사용한다. Google이 제공한 나머지 콘텐츠는 별도 허용 근거 없이 영구 저장하지 않으며, 표시와 계산에 필요한 정보는 요청 처리 중 조회한다. Place ID가 오래되거나 조회되지 않으면 Google Places로 다시 검증한다.

---

## 4. travel_plans

여행 계획의 최상위 Aggregate.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| title | VARCHAR | N | 여행 제목 |
| destination_place_id | BIGINT | N | 사용자가 확정한 도시 Place FK |
| start_date | DATE | N | 시작일 |
| end_date | DATE | N | 종료일 |
| daily_start_time | TIME | N | 목적지 현지 시각 기준 하루 시작, 기본 10:00 |
| daily_end_time | TIME | N | 목적지 현지 시각 기준 하루 종료, 기본 21:00 |
| selected_hotel_id | BIGINT | Y | 추천 후 선택된 호텔 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

`user_id`는 T1 스키마에 포함하지 않는다. U1 인증 단계에서 `users` 테이블과 기존 데이터 처리 방식을 먼저 정의한 뒤 새 migration으로 추가한다.

`selected_hotel_id`의 DB nullable 결정은 유지하며, 최종 일정 생성 API에서는 호텔을 필수로 검증한다. DB의 null 허용이 호텔 없는 기본 계획 API를 의미하지는 않는다. T1의 정상 저장 fixture에도 선택 호텔을 포함한다.

T1은 계산 완료된 일정의 저장 계층을 검증한다. `arrival_time`, `departure_time`, `stay_minutes`의 NOT NULL·CHECK 제약은 처음부터 유지하고, 테스트가 완성된 일정 값을 제공한다. 운영 코드에서 임시 시각을 채우거나 T1을 위해 제약을 완화하지 않는다. T2가 Google 검증·시간표 계산을 마친 결과를 같은 저장 계층에 전달한다.

---

## 5. food_preferences

여행 계획에서 사용자가 먹고 싶은 음식.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | 여행 계획 FK |
| food_name | VARCHAR | N | 음식명 |

예:

```text
돼지국밥
회
밀면
```

초기에는 자유 텍스트로 저장한다.

향후 음식 카테고리 체계가 필요해지면 정규화한다.

---

## 6. travel_plan_days

여행 계획의 날짜별 구분.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | 여행 계획 FK |
| day_number | INT | N | Day 순서 |
| travel_date | DATE | N | 실제 날짜 |

필수 UNIQUE:

```text
(travel_plan_id, day_number)
```

---

## 7. travel_plan_places

각 날짜에 포함된 장소.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_day_id | BIGINT | N | Day FK |
| place_id | BIGINT | N | 장소 FK |
| visit_order | INT | N | 방문 순서 |
| required | BOOLEAN | N | 필수 장소 여부 |
| place_role | VARCHAR | N | 일정 안의 관광 장소 역할 (MVP: `ATTRACTION`) |
| arrival_time | TIME | N | 목적지 현지 시각 기준 도착 예정 |
| departure_time | TIME | N | 목적지 현지 시각 기준 출발 예정 |
| stay_minutes | INT | N | 기본 정책 또는 사용자 수정값으로 확정한 체류 시간 |

### PlaceRole

예:

```text
ATTRACTION
```

여행 일정 안에서 어떤 역할로 포함됐는지를 표현한다. MVP에서는 음식점과 호텔을 `TravelPlanPlace`로 저장하지 않으므로 `ATTRACTION`만 사용한다. 식사 슬롯은 아래 테이블에 저장하고 음식점 후보는 저장하지 않는다.

### travel_plan_meal_slots

Routy가 확정한 날짜별 식사 시간이다. `TravelPlanDay`에 속하며 Google 음식점 콘텐츠나 음식점 Place 참조를 저장하지 않는다.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_day_id | BIGINT | N | Day FK |
| meal_type | VARCHAR(10) | N | `LUNCH` 또는 `DINNER` |
| start_time | TIME | N | 목적지 현지 시각 기준 확정 식사 시작 |
| end_time | TIME | N | 목적지 현지 시각 기준 확정 식사 종료 |

`(travel_plan_day_id, meal_type)` UNIQUE, `meal_type IN ('LUNCH', 'DINNER')` CHECK, `start_time < end_time` CHECK를 적용한다. 시작·종료 시각은 분 단위이며 현재 MVP의 식사 길이는 60분이다. 하루 범위와 식사 허용 시간대 안에 있는지는 Service에서 검증한다. 허용 시간대와 `reservedMinutes`는 현재 정책과 저장 시각으로 응답을 구성하며 중복 컬럼으로 저장하지 않는다.

T1은 식사 슬롯까지 포함한 완성 일정 fixture로 저장·조회·교체·삭제를 검증한다. 실제 migration 작성 시 해당 테이블을 포함하고, 이미 적용된 migration이 있으면 새 버전을 추가한다. 음식점 검색에 필요한 주변 방문 장소와 호텔은 저장된 일정에서 확보하며, 검색 결과로 식사 시각을 변경하지 않는다.

---

## 8. users

회원 기능 구현 시 추가한다.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| email | VARCHAR | N | 로그인 이메일 |
| password | VARCHAR | N | 암호화 비밀번호 |
| nickname | VARCHAR | N | 닉네임 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

Unique:

```text
email
```

비밀번호는 BCrypt 등 안전한 해시를 적용한 값만 저장한다.

---

## 9. 추천 후보 데이터 저장 여부

초기 MVP에서는 AI나 Google Places가 반환한 모든 후보를 영구 저장하지 않는다.

사용자가 최종 선택하여 여행 계획에 포함한 장소의 Google Place ID만 내부 `Place` 참조로 저장한다.

필요성이 생기면 다음과 같은 테이블을 추가할 수 있다.

```text
place_recommendation_logs
hotel_recommendation_logs
restaurant_recommendation_logs
```

하지만 자소서용 첫 프로젝트에서 필요 이상으로 테이블을 늘리지 않는다.

---

## 10. 좌표 사용

좌표는 Google Places 상세 조회 결과를 요청 처리 중 DTO로 사용한다.

```java
BigDecimal latitude;
BigDecimal longitude;
```

좌표 DTO에서는 범위를 validation하고, 경로 알고리즘에서 삼각함수를 계산할 때만 `double`로 변환한다. Google Places 콘텐츠 저장 정책에 따라 좌표를 Place Entity에 영구 저장하지 않는다.

MySQL Spatial Type은 필요성이 확인되기 전까지 도입하지 않는다.

---

## 11. Index 후보

MVP 기능이 완성된 후 실제 Query를 확인하면서 추가한다.

초기 후보:

```text
places(google_place_id) UNIQUE
travel_plans(user_id)  # U1에서 user_id 추가 후 검토
travel_plan_days(travel_plan_id)
travel_plan_places(travel_plan_day_id)
```

인덱스는 "왠지 필요할 것 같아서" 추가하지 않고 실제 조회 패턴을 근거로 추가한다.

---

## 12. 데이터 무결성 제약

애플리케이션 validation만으로 데이터 무결성을 보장하지 않는다. DB 제약조건도 함께 적용한다.

| 테이블 | 제약조건 | 목적 |
|---|---|---|
| `places` | `google_place_id` UNIQUE, NOT NULL | 동일 Google 장소 참조의 중복 저장 방지 |
| `travel_plans` | `title`, `destination_place_id`, `start_date`, `end_date`, `daily_start_time`, `daily_end_time` NOT NULL | 불완전한 계획 방지 |
| `travel_plans` | `start_date <= end_date` CHECK | 잘못된 여행 기간 방지 |
| `travel_plans` | `daily_start_time < daily_end_time` CHECK | 잘못된 일일 시간 범위 방지 |
| `food_preferences` | `(travel_plan_id, food_name)` UNIQUE | 같은 음식 선호의 중복 저장 방지 |
| `travel_plan_days` | `(travel_plan_id, day_number)` UNIQUE | 같은 Day 번호 중복 방지 |
| `travel_plan_days` | `(travel_plan_id, travel_date)` UNIQUE | 같은 날짜 중복 방지 |
| `travel_plan_places` | `(travel_plan_day_id, visit_order)` UNIQUE | 하루 방문 순서 중복 방지 |
| `travel_plan_places` | `arrival_time < departure_time` CHECK | 잘못된 방문 시간 방지 |
| `travel_plan_places` | `stay_minutes > 0` CHECK | 0 이하 체류 시간 방지 |
| `users` | `email` UNIQUE, NOT NULL | 계정 중복 방지 |

외래키의 삭제 정책은 Aggregate 생명주기에 맞춘다.

- `TravelPlan` 삭제 시 `FoodPreference`, `TravelPlanDay`, `TravelPlanPlace`, `TravelPlanMealSlot`은 함께 삭제한다. 일정 재계산으로 Day를 교체할 때 식사 슬롯도 같은 트랜잭션에서 교체하며 실패하면 기존 일정과 슬롯을 보존한다.
- `Place`는 여러 일정에서 공유하므로 참조 중일 때 삭제하지 않는다. API는 `409 Conflict`를 반환한다.
- `User` 삭제 정책은 회원 기능을 설계할 때 별도 ADR로 결정한다.

---

## 13. 스키마 변경과 마이그레이션

첫 스키마부터 Flyway versioned migration으로 관리한다. 모든 profile에서 Hibernate는 schema를 생성·변경하지 않으며, DB 변경은 migration으로만 수행한다.

```text
src/main/resources/db/migration/
├── V1__create_places.sql
├── V2__create_travel_plan_tables.sql
└── V3__add_indexes_and_constraints.sql
```

규칙:

- 이미 적용된 migration 파일은 수정하지 않는다. 변경은 새 버전 파일로 추가한다.
- migration은 로컬 빈 DB에서 적용하고, 애플리케이션 통합 테스트로 검증한다.
- `local`, `test`, `prod` profile 모두 `ddl-auto: validate`를 사용하며 `update`와 `create`를 사용하지 않는다.
- 롤백이 필요한 파괴적 변경은 사전 백업·호환 기간·복구 절차를 별도 작업으로 계획한다.

---

## 14. JPA 연관관계 원칙

- 기본적으로 필요한 방향만 연관관계를 둔다.
- 모든 관계를 양방향으로 만들지 않는다.
- Entity를 Controller Response로 직접 반환하지 않는다.
- Collection 연관관계는 Lazy Loading을 기본으로 고려한다.
- Cascade / orphanRemoval은 Aggregate 생명주기가 명확할 때만 사용한다.

예:

`TravelPlan`과 `TravelPlanDay`는 계획 삭제 시 Day도 함께 삭제되어야 하므로 생명주기를 함께 관리할 수 있다.

반대로 `Place`는 여러 여행 계획이 공유할 수 있으므로 `TravelPlanPlace` 삭제와 함께 Place를 삭제하면 안 된다.
