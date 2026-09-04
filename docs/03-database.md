# 데이터베이스 설계

## 1. 기본 원칙

초기 MVP에서는 데이터 모델을 과도하게 분리하지 않는다.

특히 관광지, 호텔, 음식점은 모두 다음 공통 특성을 가진다.

- 이름
- 주소
- 위도
- 경도
- 장소 타입

따라서 초기에는 하나의 `Place` Entity로 통합하고 `PlaceType`으로 구분한다.

---

## 2. 전체 관계

```text
User
 │
 │ 1:N
 ▼
TravelPlan
 │
 ├──────── 1:1 ──────── TravelPreference
 │
 ├──────── 1:N ──────── FoodPreference
 │
 └──────── 1:N
             ▼
       TravelPlanDay
             │
             │ 1:N
             ▼
      TravelPlanPlace
             │
             │ N:1
             ▼
           Place
```

회원 기능 구현 전에는 `TravelPlan.user_id`를 nullable로 두거나 User 연관관계를 나중에 추가할 수 있다.

초기 학습 단계에서는 회원 기능 때문에 핵심 여행 로직 개발이 막히지 않도록 한다.

---

## 3. places

장소 공통 테이블.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| name | VARCHAR | N | 장소명 |
| type | VARCHAR / ENUM | N | 장소 타입 |
| category | VARCHAR | Y | 세부 카테고리 |
| region | VARCHAR | N | 지역 |
| address | VARCHAR | Y | 주소 |
| latitude | DECIMAL | N | 위도 |
| longitude | DECIMAL | N | 경도 |
| rating | DECIMAL | Y | 평점 |
| description | TEXT | Y | 설명 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

### PlaceType

```text
ATTRACTION
HOTEL
RESTAURANT
CAFE
```

### 장소 타입별 추가 데이터

초기에는 공통 필드만 사용한다.

호텔 가격이나 음식점 메뉴처럼 도메인별 속성이 크게 늘어나면 다음 중 하나를 검토한다.

1. `HotelDetail`, `RestaurantDetail` 같은 1:1 보조 Entity
2. 별도 Entity 분리

MVP에서는 미리 복잡하게 분리하지 않는다.

---

## 4. travel_plans

여행 계획의 최상위 Aggregate.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| user_id | BIGINT | Y* | 사용자 FK |
| title | VARCHAR | N | 여행 제목 |
| region | VARCHAR | N | 여행 지역 |
| start_date | DATE | N | 시작일 |
| end_date | DATE | N | 종료일 |
| selected_hotel_id | BIGINT | Y | 추천 후 선택된 호텔 |
| created_at | DATETIME | N | 생성 시각 |
| updated_at | DATETIME | N | 수정 시각 |

`user_id`는 회원 기능 구현 전에는 제외하거나 nullable로 둘 수 있다.

`selected_hotel_id`는 초기에는 선택적으로 사용한다.

---

## 5. travel_preferences

여행 계획 단위의 사용자 선호.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | 여행 계획 FK |
| crowd_preference | VARCHAR | Y | 혼잡도 선호 |
| original_text | TEXT | Y | 사용자 원문 |
| created_at | DATETIME | N | 생성 시각 |

### 여행 관심사 저장

`interests`가 복수 값이므로 초기 구현에서 두 가지 선택지가 있다.

#### 선택 A - ElementCollection 사용

```text
travel_preference_interests
- travel_preference_id
- interest
```

장점:

- 정규화 가능
- JPA 연습 가능

#### 선택 B - 별도 Entity

관심사에 추가 속성이 필요해질 때 사용한다.

MVP에서는 A를 우선 검토한다.

예시 Interest:

```text
NATURE
HISTORY
PHOTOGRAPHY
SHOPPING
ACTIVITY
RELAX
FOOD
```

---

## 6. food_preferences

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

## 7. travel_plan_days

여행 계획의 날짜별 구분.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_id | BIGINT | N | 여행 계획 FK |
| day_number | INT | N | Day 순서 |
| travel_date | DATE | N | 실제 날짜 |

Unique 권장:

```text
(travel_plan_id, day_number)
```

---

## 8. travel_plan_places

각 날짜에 포함된 장소.

| Column | Type 예시 | Nullable | 설명 |
|---|---|---:|---|
| id | BIGINT | N | PK |
| travel_plan_day_id | BIGINT | N | Day FK |
| place_id | BIGINT | N | 장소 FK |
| visit_order | INT | N | 방문 순서 |
| required | BOOLEAN | N | 필수 장소 여부 |
| place_role | VARCHAR | N | 관광/식사/숙소 등 |
| arrival_time | TIME | Y | 도착 예정 |
| departure_time | TIME | Y | 출발 예정 |
| stay_minutes | INT | Y | 예상 체류 시간 |

### PlaceRole

예:

```text
ATTRACTION
MEAL
HOTEL
```

`Place.type`과 달리 여행 일정 안에서 어떤 역할로 포함됐는지를 표현한다.

---

## 9. users

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

## 10. 추천 후보 데이터 저장 여부

초기 MVP에서는 AI나 추천 알고리즘이 생성한 모든 후보를 영구 저장하지 않는다.

사용자가 최종 선택하여 여행 계획에 포함한 장소 위주로 저장한다.

필요성이 생기면 다음과 같은 테이블을 추가할 수 있다.

```text
place_recommendation_logs
hotel_recommendation_logs
restaurant_recommendation_logs
```

하지만 자소서용 첫 프로젝트에서 필요 이상으로 테이블을 늘리지 않는다.

---

## 11. 좌표 타입

초기에는 JPA 학습 난이도를 낮추기 위해 다음처럼 구현할 수 있다.

```java
BigDecimal latitude;
BigDecimal longitude;
```

또는 학습 편의를 위해 `double`을 사용할 수 있으나 DB 정밀도와 의미를 고려해 최종적으로 선택한다.

MySQL Spatial Type은 필요성이 확인되기 전까지 도입하지 않는다.

---

## 12. Index 후보

MVP 기능이 완성된 후 실제 Query를 확인하면서 추가한다.

초기 후보:

```text
places(region, type)
places(name)
travel_plans(user_id)
travel_plan_days(travel_plan_id)
travel_plan_places(travel_plan_day_id)
```

인덱스는 "왠지 필요할 것 같아서" 추가하지 않고 실제 조회 패턴을 근거로 추가한다.

---

## 13. 데이터 무결성 제약

애플리케이션 validation만으로 데이터 무결성을 보장하지 않는다. DB 제약조건도 함께 적용한다.

| 테이블 | 제약조건 | 목적 |
|---|---|---|
| `places` | `name`, `type`, `region`, `latitude`, `longitude` NOT NULL | 추천·거리 계산에 필요한 최소 데이터 보장 |
| `places` | latitude `[-90, 90]`, longitude `[-180, 180]` CHECK | 유효하지 않은 좌표 방지 |
| `travel_plans` | `title`, `region`, `start_date`, `end_date` NOT NULL | 불완전한 계획 방지 |
| `travel_plans` | `start_date <= end_date` CHECK | 잘못된 여행 기간 방지 |
| `travel_preferences` | `travel_plan_id` UNIQUE, NOT NULL | 하나의 계획에 선호 하나만 연결 |
| `food_preferences` | `(travel_plan_id, food_name)` UNIQUE | 같은 음식 선호의 중복 저장 방지 |
| `travel_plan_days` | `(travel_plan_id, day_number)` UNIQUE | 같은 Day 번호 중복 방지 |
| `travel_plan_days` | `(travel_plan_id, travel_date)` UNIQUE | 같은 날짜 중복 방지 |
| `travel_plan_places` | `(travel_plan_day_id, visit_order)` UNIQUE | 하루 방문 순서 중복 방지 |
| `travel_plan_places` | `stay_minutes >= 0` CHECK | 음수 체류 시간 방지 |
| `users` | `email` UNIQUE, NOT NULL | 계정 중복 방지 |

외래키의 삭제 정책은 Aggregate 생명주기에 맞춘다.

- `TravelPlan` 삭제 시 `TravelPreference`, `FoodPreference`, `TravelPlanDay`, `TravelPlanPlace`는 함께 삭제한다.
- `Place`는 여러 일정에서 공유하므로 참조 중일 때 삭제하지 않는다. API는 `409 Conflict`를 반환한다.
- `User` 삭제 정책은 회원 기능을 설계할 때 별도 ADR로 결정한다.

---

## 14. 스키마 변경과 마이그레이션

개발 초기 Entity 설계 검증 단계까지만 `ddl-auto: update`를 임시 사용한다. 공유 DB·테스트·운영 환경에서는 Flyway 마이그레이션으로 스키마를 관리한다.

```text
src/main/resources/db/migration/
├── V1__create_places.sql
├── V2__create_travel_plan_tables.sql
└── V3__add_indexes_and_constraints.sql
```

규칙:

- 이미 적용된 migration 파일은 수정하지 않는다. 변경은 새 버전 파일로 추가한다.
- migration은 로컬 빈 DB에서 적용하고, 애플리케이션 통합 테스트로 검증한다.
- 운영 프로필에서는 `ddl-auto: validate`를 사용하며 `update`와 `create`를 사용하지 않는다.
- 롤백이 필요한 파괴적 변경은 사전 백업·호환 기간·복구 절차를 별도 작업으로 계획한다.

---

## 15. JPA 연관관계 원칙

- 기본적으로 필요한 방향만 연관관계를 둔다.
- 모든 관계를 양방향으로 만들지 않는다.
- Entity를 Controller Response로 직접 반환하지 않는다.
- Collection 연관관계는 Lazy Loading을 기본으로 고려한다.
- Cascade / orphanRemoval은 Aggregate 생명주기가 명확할 때만 사용한다.

예:

`TravelPlan`과 `TravelPlanDay`는 계획 삭제 시 Day도 함께 삭제되어야 하므로 생명주기를 함께 관리할 수 있다.

반대로 `Place`는 여러 여행 계획이 공유할 수 있으므로 `TravelPlanPlace` 삭제와 함께 Place를 삭제하면 안 된다.
