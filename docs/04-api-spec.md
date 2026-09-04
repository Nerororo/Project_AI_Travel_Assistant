# REST API 명세

## 1. 공통 원칙

Base Path:

```text
/api
```

Request / Response는 JSON을 기본으로 한다.

Entity를 직접 응답하지 않고 Response DTO를 사용한다.

### 성공 상태 코드

| 상황 | 상태 코드 |
|---|---|
| 단건/목록 조회, 수정 성공 | `200 OK` |
| 생성 성공 | `201 Created` |
| 삭제 성공 | `204 No Content` |

`POST`로 리소스를 생성하면 응답 헤더의 `Location`에 생성된 리소스 URI를 넣는다.

### 오류 응답

에러 응답 형식은 Global Exception Handler 도입 시 아래 형식으로 통일한다.

예:

```json
{
  "code": "PLACE_NOT_FOUND",
  "message": "장소를 찾을 수 없습니다.",
  "fieldErrors": []
}
```

| 상황 | 상태 코드 | 오류 코드 예시 |
|---|---|---|
| 필수값·형식·범위 검증 실패 | `400 Bad Request` | `VALIDATION_FAILED` |
| 존재하지 않는 리소스 | `404 Not Found` | `PLACE_NOT_FOUND`, `TRAVEL_PLAN_NOT_FOUND` |
| 중복되거나 현재 상태에서 수행할 수 없는 요청 | `409 Conflict` | `DUPLICATE_VISIT_ORDER` |
| AI 제공자 장애 | `503 Service Unavailable` | `AI_UNAVAILABLE` |

`fieldErrors`는 요청 필드 검증에 실패한 경우에만 채운다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "fieldErrors": [
    {
      "field": "name",
      "reason": "must not be blank"
    }
  ]
}
```

### 날짜·ID·목록 요청 규칙

- 날짜는 ISO-8601의 `yyyy-MM-dd` 형식으로 전달한다.
- Path Variable과 ID 배열의 ID는 양의 정수여야 한다.
- 배열형 입력값은 중복을 허용하지 않는다. 중복 값은 `400`으로 처리한다.
- 목록 API의 기본 정렬은 `id,asc`이며, 지원하는 정렬 필드는 엔드포인트별로 명시한다.

---

## 2. 현재 확인용 API

### GET `/hello`

Spring Boot 기본 연결 확인용 API.

프로젝트 초기 확인이 끝나면 제거하거나 개발용으로만 유지할 수 있다.

---

# 3. Place API

## GET `/api/places`

장소 목록 조회.

### Query Parameters 예시

```text
region=부산
type=ATTRACTION
category=BEACH
page=0
size=20
sort=id,asc
```

| 파라미터 | 기본값 | 제약 |
|---|---:|---|
| `page` | `0` | 0 이상 |
| `size` | `20` | 1 이상 100 이하 |
| `sort` | `id,asc` | `id`, `name`, `region`만 허용 |

### Response 예시

```json
{
  "content": [
    {
      "id": 1,
      "name": "해운대",
      "type": "ATTRACTION",
      "category": "BEACH",
      "region": "부산",
      "latitude": 35.1587,
      "longitude": 129.1604
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## GET `/api/places/{placeId}`

장소 상세 조회.

---

## POST `/api/places`

개발 / 관리자 용도의 장소 등록.

### Request

```json
{
  "name": "해운대",
  "type": "ATTRACTION",
  "category": "BEACH",
  "region": "부산",
  "address": "부산광역시 해운대구",
  "latitude": 35.1587,
  "longitude": 129.1604,
  "description": "부산의 대표적인 해변"
}
```

### Validation

| 필드 | 규칙 |
|---|---|
| `name`, `region`, `type` | 필수 |
| `name`, `region` | 앞뒤 공백 제거 후 1~100자 |
| `type` | `ATTRACTION`, `HOTEL`, `RESTAURANT`, `CAFE` 중 하나 |
| `latitude` | -90 이상 90 이하 |
| `longitude` | -180 이상 180 이하 |
| `rating` | 제공 시 0 이상 5 이하 |

### Response

```json
{
  "id": 1,
  "name": "해운대"
}
```

성공 시 `201 Created`를 반환한다.

---

## PUT `/api/places/{placeId}`

장소 수정.

---

## DELETE `/api/places/{placeId}`

장소 삭제.

---

# 4. AI 선호 분석 API

## POST `/api/ai/preferences`

사용자의 자연어 여행 선호를 구조화한다.

### Request

```json
{
  "text": "사람 많은 곳은 싫고 자연과 사진 찍는 것을 좋아해"
}
```

`text`는 앞뒤 공백 제거 후 1~1,000자여야 한다.

### Response

```json
{
  "interests": [
    "NATURE",
    "PHOTOGRAPHY"
  ],
  "crowdPreference": "LOW"
}
```

### 응답 스키마

| 필드 | 규칙 |
|---|---|
| `interests` | 중복 없는 배열. `NATURE`, `HISTORY`, `PHOTOGRAPHY`, `SHOPPING`, `ACTIVITY`, `RELAX`, `FOOD`만 허용 |
| `crowdPreference` | `LOW`, `MEDIUM`, `HIGH`, `ANY` 중 하나 |

선호를 판단할 수 없는 경우 `interests`는 빈 배열, `crowdPreference`는 `ANY`를 반환한다. AI가 이 계약을 만족하지 못하면 서버는 `503`과 `AI_RESPONSE_INVALID`를 반환하며 임의의 기본 선호로 대체하지 않는다.

timeout 또는 제공자 장애는 `503`과 `AI_UNAVAILABLE`을 반환한다. 네트워크 오류와 제공자 5xx에 한해 최대 한 번 재시도하며, 클라이언트는 재시도 사실을 알 수 없다.

이 API는 개발 / 검증 단계에서 AI 분석 기능을 독립 테스트하기 위한 용도로 사용할 수 있다.

최종 여행 계획 API에서는 내부 Service 호출로 사용할 수 있다.

---

# 5. 여행지 추천 API

## POST `/api/recommendations/places`

### Request

```json
{
  "region": "서울",
  "interests": [
    "HISTORY",
    "PHOTOGRAPHY"
  ],
  "crowdPreference": "LOW",
  "requiredPlaceIds": [
    1
  ]
}
```

### Response

```json
{
  "places": [
    {
      "id": 1,
      "name": "경복궁",
      "required": true,
      "reason": "필수 방문 장소"
    },
    {
      "id": 5,
      "name": "북촌한옥마을",
      "required": false,
      "reason": "역사와 사진 선호에 적합"
    }
  ]
}
```

---

# 6. 경로 최적화 API

## POST `/api/routes/optimize`

알고리즘 검증 및 독립 테스트를 위한 API.

### Request

```json
{
  "startPlaceId": 10,
  "placeIds": [1, 5, 8, 15]
}
```

### Response

```json
{
  "route": [
    {
      "order": 1,
      "placeId": 10
    },
    {
      "order": 2,
      "placeId": 5
    },
    {
      "order": 3,
      "placeId": 1
    },
    {
      "order": 4,
      "placeId": 15
    },
    {
      "order": 5,
      "placeId": 8
    }
  ],
  "totalDistanceKm": 25.3,
  "algorithm": "NEAREST_NEIGHBOR"
}
```

향후 2-opt를 적용하면 알고리즘 이름과 개선 전후 거리를 비교할 수 있다.

---

# 7. 호텔 추천 API

## POST `/api/recommendations/hotels`

### Request

```json
{
  "region": "부산",
  "placeIds": [1, 5, 8, 15]
}
```

### Response

```json
{
  "hotels": [
    {
      "placeId": 44,
      "name": "Example Hotel",
      "totalDistanceKm": 15.4,
      "rank": 1
    }
  ]
}
```

MVP에서는 이동 거리 기준을 우선한다.

---

# 8. 음식점 추천 API

## POST `/api/recommendations/restaurants`

### Request

```json
{
  "food": "돼지국밥",
  "routePlaceIds": [1, 4, 8]
}
```

### Response

```json
{
  "restaurants": [
    {
      "placeId": 81,
      "name": "OO돼지국밥",
      "foodMatched": true,
      "detourDistanceKm": 0.8,
      "rank": 1
    }
  ]
}
```

초기에는 음식 일치 + 동선 이탈 거리만 사용한다.

---

# 9. TravelPlan API

## POST `/api/travel-plans`

최종 핵심 API.

### Request

```json
{
  "region": "부산",
  "startDate": "2026-10-01",
  "endDate": "2026-10-03",
  "preferenceText": "바다와 사진 찍는 것을 좋아하고 사람이 너무 많은 곳은 싫어",
  "requiredPlaceIds": [1, 2],
  "selectedRecommendedPlaceIds": [5, 8],
  "foods": [
    "돼지국밥",
    "회"
  ]
}
```

### Validation 및 일정 생성 규칙

| 항목 | 규칙 | 실패 코드 |
|---|---|---|
| 여행 기간 | `startDate` 이상 `endDate`, 1~14일 | `INVALID_TRAVEL_PERIOD` |
| 방문 장소 | 필수·선택 장소를 합쳐 중복 없이 1개 이상 | `INVALID_PLACE_SELECTION` |
| 필수 장소 | 최종 일정에 모두 포함 | `REQUIRED_PLACE_MISSING` |
| 일일 장소 수 | 식사 장소 포함 최대 6개 | `PLAN_CAPACITY_EXCEEDED` |
| 전체 장소 수 | 여행 일수 × 6 이하 | `PLAN_CAPACITY_EXCEEDED` |
| 음식 목록 | 중복 없이 최대 1~5개, 각 값 1~50자 | `VALIDATION_FAILED` |

일정은 날짜별 장소 수 차이가 최대 1개가 되도록 앞선 날짜부터 배치한다. 각 날짜의 경로는 그 날짜에 배치된 장소만 대상으로 계산한다.

MVP의 `assumptions`는 영업시간, 실시간 교통, 실제 도로 이동 시간, 체류 시간 기반 시간표를 최적화에 반영하지 않았음을 명시한다. 숙소는 모든 날짜의 출발·종료 기준점이지만 방문 순서에는 포함하지 않는다. 음식점은 자동 확정하지 않고 추천 후보로만 반환한다.

### 처리 흐름

```text
입력 Validation
 ↓
선호 분석
 ↓
여행지 확정
 ↓
호텔 추천
 ↓
날짜별 장소 분배
 ↓
날짜별 경로 최적화
 ↓
음식점 추천
 ↓
TravelPlan 저장
 ↓
Response 반환
```

### Response 예시

```json
{
  "travelPlanId": 1,
  "title": "부산 2박 3일 여행",
  "region": "부산",
  "hotel": {
    "placeId": 44,
    "name": "Example Hotel"
  },
  "assumptions": [
    "영업시간과 실시간 교통은 반영하지 않았습니다.",
    "음식점은 추천 후보이며 일정에 자동 확정되지 않습니다."
  ],
  "days": [
    {
      "day": 1,
      "date": "2026-10-01",
      "places": [
        {
          "order": 1,
          "placeId": 1,
          "name": "해운대",
          "role": "ATTRACTION"
        },
        {
          "order": 2,
          "placeId": 81,
          "name": "OO돼지국밥",
          "role": "MEAL"
        }
      ]
    }
  ]
}
```

---

## GET `/api/travel-plans/{travelPlanId}`

여행 계획 상세 조회.

---

## PUT `/api/travel-plans/{travelPlanId}`

여행 계획 기본 정보 또는 선택 장소 수정.

MVP에서는 수정 범위를 단순하게 유지한다.

변경 후 경로 재계산이 필요한 경우 별도 Service에서 처리한다.

---

## DELETE `/api/travel-plans/{travelPlanId}`

여행 계획 삭제.

---

# 10. User / Auth API

핵심 MVP 완료 후 추가한다.

## POST `/api/users`

회원가입.

## POST `/api/auth/login`

로그인.

## GET `/api/users/me/travel-plans`

현재 사용자의 여행 계획 조회.

---

# 11. API 구현 우선순위

```text
1. GET /hello                 [현재 존재]
2. Place CRUD
3. TravelPlan 기본 CRUD
4. POST /api/ai/preferences
5. POST /api/recommendations/places
6. POST /api/routes/optimize
7. POST /api/recommendations/hotels
8. POST /api/recommendations/restaurants
9. 최종 POST /api/travel-plans 통합
10. Auth / User
```

개별 기능 API를 먼저 만들고 충분히 테스트한 다음 최종 `POST /api/travel-plans`에서 조합한다.

---

# 12. 엔드포인트별 상태 코드

| 엔드포인트 | 성공 | 주요 실패 |
|---|---|---|
| `GET /api/places` | `200` | `400`(잘못된 page/size/sort) |
| `GET /api/places/{placeId}` | `200` | `404` |
| `POST /api/places` | `201` | `400` |
| `PUT /api/places/{placeId}` | `200` | `400`, `404` |
| `DELETE /api/places/{placeId}` | `204` | `404`, `409`(참조 중인 장소) |
| `POST /api/ai/preferences` | `200` | `400`, `503` |
| `POST /api/recommendations/*` | `200` | `400`, `404`, `503`(AI 사용 시) |
| `POST /api/routes/optimize` | `200` | `400`, `404` |
| `POST /api/travel-plans` | `201` | `400`, `404`, `409`, `503` |
| `GET/PUT/DELETE /api/travel-plans/{travelPlanId}` | `200`/`200`/`204` | `400`, `404`, `409` |
