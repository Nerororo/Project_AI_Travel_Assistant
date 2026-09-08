# 시스템 아키텍처

## 1. 현재 프로젝트 기반

현재 `travel` 프로젝트는 다음 환경을 기준으로 한다.

```text
Java 21
Spring Boot 4.1.1
Gradle
Spring Data JPA
MySQL 8.4
Docker Compose
Base Package: com.example.travel
```

현재 기본 Spring Boot 실행 환경과 DB 개발 환경을 기반으로 기능을 단계적으로 확장한다.

---

## 2. 전체 시스템 구조

```text
                     Client
                       │
                       │ HTTP / JSON
                       ▼
                Spring Boot API
                       │
              ┌────────┼────────┐
              │        │        │
              ▼        ▼        ▼
       Domain Services AI Service Google Places
              │        │
              │        ▼
              │     OpenAI API
              ▼
       Repository / JPA
              │
              ▼
            MySQL
```

국가·도시·관광지·호텔·음식점 검색과 화면의 지도 표시는 Google Maps Platform을 사용한다. Google Places 통신은 `place/client`에 격리하고, 자동 테스트에서는 fake client로 대체한다.

```text
Place Service ──────► Google Places API
```

---

## 3. Spring 요청 처리 기본 흐름

```text
HTTP Request
     │
     ▼
Controller
     │
     ▼
Service
     │
     ▼
Repository
     │
     ▼
JPA
     │
     ▼
MySQL
```

### Controller

책임:

- URL Mapping
- Request DTO 수신
- Validation
- Service 호출
- Response DTO 반환

Controller가 직접 다음 작업을 수행하지 않는다.

- 경로 알고리즘 실행
- JPA Repository 조합
- OpenAI Prompt 설계
- 추천 점수 계산

---

### Service

책임:

- 비즈니스 규칙
- 여러 Repository 또는 다른 Service 조합
- Transaction 경계
- 여행 일정 생성 흐름 관리

---

### Repository

책임:

- Entity 저장
- Entity 조회
- 조건 기반 DB 검색

Repository에 비즈니스 규칙을 작성하지 않는다.

---

## 4. 패키지 구조

현재 Base Package인 `com.example.travel`을 유지한다.

```text
src/main/java/com/example/travel
│
├── TravelApplication.java
│
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/
│   └── dto/
│
├── place/
│   ├── controller/
│   ├── service/
│   ├── client/
│   ├── repository/
│   ├── domain/
│   └── dto/
│
├── travelplan/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/
│   └── dto/
│
├── recommendation/
│   ├── service/
│   └── dto/
│
├── route/
│   ├── service/
│   ├── client/
│   ├── algorithm/
│   └── dto/
│
├── ai/
│   ├── service/
│   ├── client/
│   └── dto/
│
└── global/
    ├── config/
    ├── exception/
    ├── security/
    └── common/
```

패키지는 해당 기능을 개발하는 시점에 생성한다. 빈 폴더를 한꺼번에 만들 필요는 없다.

---

## 5. 각 도메인의 책임

### place

Google Places에서 국가, 도시, 관광지, 호텔, 음식점을 검색하고 검증한다. 영구 저장 대상은 Google Places 콘텐츠 전체가 아니라 Google Place ID를 가진 내부 `Place` 참조다.

```text
Place
├── ATTRACTION
├── HOTEL
├── RESTAURANT
└── CAFE
```

---

### 국가와 도시 선택 데이터의 위치

국가·도시·장소의 Google Place ID 검증과 조회 DTO는 `place` 패키지가 소유한다. 국가를 바탕으로 도시 이름과 추천 이유를 생성하는 OpenAI 계약은 `ai` 패키지가 소유한다. 선택된 도시와 방문 장소의 내부 참조는 `travelplan`이 저장한다.

여행 취향과 혼잡도를 입력받지 않으므로 `preference` 패키지와 `TravelPreference` Entity는 만들지 않는다.

---

### ai

국가를 기준으로 3~5개의 도시 후보 이름과 추천 이유를 구조화해 생성하는 OpenAI 통신을 담당한다.

```text
검증된 국가 정보로 Prompt 생성
    ↓
OpenAI API 호출
    ↓
구조화된 Response 수신
    ↓
도시 후보 DTO 변환
```

AI 패키지는 다음 기능을 구현하지 않는다.

- 거리 계산
- 경로 최적화
- 호텔 점수 계산
- 음식점 동선 점수 계산
- TravelPlan DB 저장

#### AI Client 경계

```text
AiService
   │
   ▼
AiClient (interface)
   ├── OpenAiClient       : 운영 OpenAI HTTP 통신
   └── FakeAiClient       : 테스트/로컬의 결정적 응답
```

`AiService`는 프롬프트 구성, `AiClient` 호출, 도시 후보 DTO 검증, 예외 변환을 담당한다. `OpenAiClient`만 HTTP 요청·인증 헤더·timeout·재시도를 담당한다. AI가 만든 도시 이름은 `PlaceService`가 Google Places로 검증한 뒤에만 사용자에게 반환한다.

AI 응답은 JSON Schema 또는 provider의 structured output으로 제한한다. DTO validation에 실패하면 추천·저장 흐름을 중단하고 `AI_RESPONSE_INVALID` 예외로 변환한다.

---

### route와 시간표

`route/client`는 Google Routes에서 정적 이동 거리·시간 행렬을 가져온다. `route/algorithm`은 HTTP Client나 Spring에 의존하지 않고 전달받은 행렬로 방문 순서를 계산한다.

`TravelPlanService`는 `RouteService`가 반환한 순서와 이동 시간을 이용해 목적지 현지 시각 기준 시간표를 조합한다. 장소 유형별 기본 체류 시간과 사용자 수정값의 선택은 순수 Java `StayDurationPolicy`가 담당한다.

음식점 후보 수집은 `RecommendationService`가 `PlaceService`를 통해 수행한다. 일반 식사 슬롯은 직전·직후 장소 사이의 이탈 시간을 계산하고, 긴 체류가 식사 시간대 전체를 포함하면 해당 장소 내부 후보를 우선한 뒤 인접 후보로 대체한다.

`StayDurationPolicy`의 체류 시간은 전체 체류 구간을 뜻한다. 시간표 조합은 긴 체류 안의 식사·인접 음식점 왕복 이동을 중복 합산하지 않는다. `RecommendationService`는 저장된 식사 시각과 체류·주변 방문 경계를 이용해 후보의 시간 충족 여부를 검증하며, 후보에 맞춰 시간표를 변경하지 않는다. 구체 합산·경계 식은 `01-requirements.md`의 FR-11을 따른다.

---

### route

순수한 위치 / 경로 계산을 담당한다.

```text
좌표
 ↓
거리 계산
 ↓
Distance Matrix
 ↓
Nearest Neighbor
 ↓
2-opt (후속)
```

가능하면 Spring / DB / OpenAI에 의존하지 않는 순수 Java 알고리즘으로 작성한다.

이렇게 하면 단위 테스트가 쉽고 알고리즘 자체를 비교하기도 쉽다.

---

### recommendation

저장 일정 기반 음식점 검색의 HTTP 처리는 `travelplan/controller`, 조회·조합은 `travelplan/service`가 담당한다. 해당 Service는 자체 내부 조회 계층에서 계획·날짜·슬롯을 검증하고 DB 조회 트랜잭션을 끝낸 뒤, 도시·호텔·하루 범위·방문 시각·식사 시각·음식을 `recommendation`의 전달 DTO로 구성해 공개 Service에 넘긴다. `recommendation`은 TravelPlan Entity·Repository·Service를 역으로 참조하지 않는다. 외부 장소 조회와 이동 비용은 각각 PlaceService·RouteService를 사용한다.

추천 관련 비즈니스 규칙을 담당한다.

```text
PlaceRecommendationService
HotelRecommendationService
RestaurantRecommendationService
```

초기에는 서비스 수가 적다면 하나의 `RecommendationService`로 시작해도 된다.

복잡해지면 역할에 따라 분리한다.

---

### travelplan

전체 여행 계획 생성 흐름을 조정하는 핵심 도메인이다.

`TravelPlanService`는 각 기능의 세부 알고리즘을 직접 구현하기보다 필요한 Service를 호출한다.

```text
TravelPlanService
     │
     ├── AiService
     │       국가 기반 도시 후보 생성 (국가 선택 흐름에서만)
     ├── RecommendationService
     ├── RouteService
     ├── PlaceService
     │       Google Place ID 검증 / 장소 정보 확보
     └── Repository
```

호텔이 선택된 날짜별 경로는 `호텔 → 관광 장소들 → 호텔`로 계산한다. `TravelPlanDay`는 관광 장소·방문 순서·시각과 `TravelPlanMealSlot`의 식사 시각을 소유한다. 호텔과 음식점 추천 후보는 일정 방문 장소로 저장하지 않는다.

상세 조회는 저장된 시간표·식사 슬롯을 내부 조회 DTO로 읽고 DB 조회 트랜잭션을 끝낸 뒤, `PlaceService`로 현재 장소 표시 정보를 보완한다. `RouteService`나 일정 배치 정책을 실행하지 않는다. 음식점 후보는 별도 검색 요청에서 `RecommendationService`로 조회하며 저장된 일정은 변경하지 않는다. 일정 조건 수정은 외부 검증·재계산 성공 후 저장 트랜잭션에서 방문 일정과 식사 슬롯을 함께 교체한다.

#### Google Place ID 검증과 저장 경계

`TravelPlanService`는 입력 목록의 형식·교차 중복·체류 시간 수정 대상 포함 여부를 검사하고, `PlaceService`에 다음 검증을 한 번에 요청한다.

```text
선택 도시: 존재 + 도시 유형
방문 장소: 존재 + 관광 유형 + 선택 도시 소속
선택 호텔: 존재 + 숙박 유형 + 선택 도시 소속
```

`PlaceService`는 Google 응답의 포함 행정구역 등 검증 가능한 근거로 도시 소속을 판정한다. 소속을 확인할 수 없는 경우 일치한다고 추측하지 않고 검증 실패로 처리한다.

외부 Google 검증과 경로·추천 계산 중에는 DB 트랜잭션을 열지 않는다. 모든 검증과 계산이 성공한 다음 트랜잭션을 시작해 `Place` 참조와 전체 TravelPlan Aggregate를 저장한다. 따라서 provider 실패나 validation 실패 시 일부 `Place`, Day, PlanPlace가 남지 않는다.

향후 AI 일정 설명이 필요하다면 `AiService`도 사용한다.

---

### user

회원가입 / 로그인 / 여행 계획 소유권을 담당한다.

MVP 핵심 기능 이후 구현한다.

---

### global

도메인과 무관한 전역 공통 기능만 포함한다.

허용:

- Configuration
- Exception Handler
- Security
- 공통 Response
- 공통 Utility

금지:

- 여행 추천
- 경로 최적화
- 호텔 추천
- 음식점 추천

---

## 6. 핵심 여행 계획 생성 흐름

```text
사용자 요청
   │
   ▼
TravelPlanController
   │
   ▼
TravelPlanService
   │
   ├──► AiService
   │       국가를 선택한 경우 도시 후보 생성
   │
   ├──► PlaceService
   │       Google Places로 도시·장소 검증
   │
   ├──► RecommendationService
   │       선택 도시의 방문 장소 후보 정렬
   │
   ├──► RouteService
   │       거리 / 방문 순서 계산
   │
   ├──► RecommendationService
   │       호텔 / 음식점 추천
   │
   ▼
TravelPlanRepository
   │
   ▼
MySQL
```

---

## 7. AI와 알고리즘 역할 분리

### AI가 잘하는 일

```text
선택 국가: 일본
      ↓
대표 여행 도시 후보 생성
      ↓
[도쿄, 오사카, 교토]
```

### 백엔드가 잘하는 일

```text
A, B, C, D의 좌표
       ↓
거리 계산
       ↓
경로 후보 계산
       ↓
총 거리 비교
       ↓
최종 방문 순서
```

이 구분은 프로젝트의 핵심 설계 원칙으로 유지한다.

---

## 8. 외부 API 추상화 방향

외부 API는 Service에 직접 HTTP 호출 코드를 흩뿌리지 않는다.

예:

```text
AiService
   │
   ▼
OpenAiClient
   │
   ▼
OpenAI API
```

Google Places API도 같은 형태로 격리한다.

```text
PlaceService
   │
   ▼
GooglePlacesClient (interface)
   ├── GooglePlacesHttpClient : 실제 통신
   └── FakeGooglePlacesClient : 자동 테스트
```

외부 서비스 변경이 핵심 비즈니스 로직에 미치는 영향을 줄이기 위한 구조다.

---

## 9. Transaction 기준

DB 변경이 하나의 비즈니스 작업으로 묶여야 할 때 Service에 Transaction을 적용한다.

예:

```text
TravelPlan 생성
   +
Day 생성
   +
방문 장소 생성
```

중간에 실패하면 불완전한 여행 계획이 저장되지 않도록 한다.

초기에는 필요한 곳에만 적용하고, 모든 메서드에 무분별하게 사용하지 않는다.

---

## 10. 예외 처리

도메인 예외를 명확하게 표현한다.

예:

```text
PlaceNotFoundException
TravelPlanNotFoundException
InvalidTravelPeriodException
RouteOptimizationException
AiResponseParsingException
```

`global/exception`의 Global Exception Handler에서 HTTP 응답으로 변환한다.

---

## 11. 테스트 전략

### Algorithm Unit Test

Spring Context 없이 실행하는 것을 우선한다.

대상:

- 거리 계산
- Nearest Neighbor
- 2-opt

### Service Unit / Integration Test

대상:

- 호텔 점수
- 음식점 추천
- 여행 계획 생성

### Repository Test

필요한 Query가 생겼을 때 작성한다.

### Controller Test

핵심 API의 Validation / Response 계약을 확인한다.
