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
              ┌────────┴────────┐
              │                 │
              ▼                 ▼
       Domain Services      AI Service
              │                 │
              │                 ▼
              │            OpenAI API
              │
              ▼
       Repository / JPA
              │
              ▼
            MySQL
```

향후 실제 장소 / 도로 이동 정보를 사용하게 되면 외부 지도 / 장소 API를 추가할 수 있다.

```text
Place Service ──────► Map / Place API
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
│   ├── repository/
│   ├── domain/
│   └── dto/
│
├── preference/
│   ├── service/
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

관광지, 호텔, 음식점 등 위치를 가진 장소 정보를 담당한다.

초기 MVP에서는 공통 `Place` 모델과 `PlaceType`으로 통합한다.

```text
Place
├── ATTRACTION
├── HOTEL
├── RESTAURANT
└── CAFE
```

---

### preference

사용자의 여행 성향을 표현한다.

예:

- 자연
- 역사
- 사진
- 쇼핑
- 휴식
- 액티비티
- 혼잡도

AI가 분석한 자연어 결과를 서버 내부에서 사용할 수 있는 구조로 변환한다.

---

### ai

OpenAI API 통신만 담당한다.

```text
Prompt 생성
    ↓
OpenAI API 호출
    ↓
구조화된 Response 수신
    ↓
AI DTO 변환
```

AI 패키지는 다음 기능을 구현하지 않는다.

- 거리 계산
- 경로 최적화
- 호텔 점수 계산
- 음식점 동선 점수 계산
- TravelPlan DB 저장

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
     ├── PreferenceService
     ├── RecommendationService
     ├── RouteService
     ├── PlaceService
     └── Repository
```

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
   │       자연어 선호 구조화
   │
   ├──► RecommendationService
   │       여행지 후보 추천
   │
   ├──► PlaceService
   │       장소 / 좌표 확보
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
"한적하면서 자연 풍경이 좋고 사진 찍기 좋은 곳"
                      ↓
              의미 / 의도 해석
                      ↓
[NATURE, PHOTOGRAPHY, LOW_CROWD]
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

향후 지도 API도 같은 형태를 사용할 수 있다.

```text
PlaceService
   │
   ▼
MapClient
   │
   ▼
External Map API
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
