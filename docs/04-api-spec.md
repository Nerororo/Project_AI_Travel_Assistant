# REST API 명세

## 1. 공통 원칙

Base Path:

```text
/api
```

Request / Response는 JSON을 기본으로 한다.

Entity를 직접 응답하지 않고 Response DTO를 사용한다.

에러 응답 형식은 Global Exception Handler 도입 시 통일한다.

예:

```json
{
  "code": "PLACE_NOT_FOUND",
  "message": "장소를 찾을 수 없습니다."
}
```

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
```

### Response 예시

```json
{
  "places": [
    {
      "id": 1,
      "name": "해운대",
      "type": "ATTRACTION",
      "category": "BEACH",
      "region": "부산",
      "latitude": 35.1587,
      "longitude": 129.1604
    }
  ]
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

### Response

```json
{
  "id": 1,
  "name": "해운대"
}
```

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
