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
| Google Places 제공자 장애 | `503 Service Unavailable` | `PLACE_PROVIDER_UNAVAILABLE` |
| Google Routes 제공자 장애 | `503 Service Unavailable` | `ROUTE_PROVIDER_UNAVAILABLE` |

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
- Routy 내부 Path Variable ID는 양의 정수여야 한다.
- Google Place ID는 앞뒤 공백이 없는 1~255자 문자열이어야 하며 서버가 Google Places로 유효성을 검증한다.
- 배열형 입력값은 중복을 허용하지 않는다. 중복 값은 `400`으로 처리한다.
- 목록 API의 기본 정렬은 `id,asc`이며, 지원하는 정렬 필드는 엔드포인트별로 명시한다.

---

## 2. 현재 확인용 API

### GET `/hello`

Spring Boot 기본 연결 확인용 API.

프로젝트 초기 확인이 끝나면 제거하거나 개발용으로만 유지할 수 있다.

---

# 3. 국가·도시·장소 API

Google Places 결과는 API Response DTO로 반환하며 Google 콘텐츠 전체를 Routy DB에 영구 복제하지 않는다. 사용자가 일정에 선택한 장소는 `googlePlaceId`를 가진 내부 Place 참조로 저장한다.

## POST `/api/destinations/cities/resolve`

Google 자동완성에서 사용자가 직접 고른 도시를 검증한다.

### Request

```json
{
  "cityGooglePlaceId": "ChIJ..."
}
```

서버는 Google Places로 장소가 실제 도시 수준의 지역인지 검증한다.

### Response

```json
{
  "googlePlaceId": "ChIJ...",
  "name": "도쿄",
  "countryCode": "JP"
}
```

## GET `/api/places/google/{googlePlaceId}`

Google Place ID로 현재 장소 상세정보를 조회한다. 응답 필드는 Google Field Mask와 저장·표시 정책을 따른다.

---

# 4. AI 도시 후보 API

## POST `/api/destinations/cities/recommend`

선택한 국가를 검증한 뒤 AI가 대표 여행 도시 이름과 추천 이유를 생성하고, 서버가 각 후보를 Google Places로 검증한다.

### Request

```json
{
  "countryGooglePlaceId": "ChIJ..."
}
```

### Response

```json
{
  "country": {
    "googlePlaceId": "ChIJ...",
    "name": "일본",
    "countryCode": "JP"
  },
  "cities": [
    {
      "googlePlaceId": "ChIJ...",
      "name": "도쿄",
      "countryCode": "JP",
      "reason": "교통과 관광 인프라가 잘 갖춰진 대표 여행 도시"
    }
  ]
}
```

AI는 3~5개의 도시 이름·국가 코드·추천 이유만 생성한다. 서버는 선택 국가와 국가 코드가 다르거나 Google Places에서 도시로 확인되지 않는 후보를 응답하지 않는다. AI 결과가 계약을 만족하지 못하면 `503`과 `AI_RESPONSE_INVALID`, timeout 또는 제공자 장애는 `503`과 `AI_UNAVAILABLE`을 반환한다.

---

# 5. 방문 장소 추천 API

## POST `/api/recommendations/places`

### Request

```json
{
  "cityGooglePlaceId": "ChIJ...",
  "size": 10,
  "requiredGooglePlaceIds": ["ChIJ..."]
}
```

`size` 기본값은 10이며 1 이상 20 이하이다. 서버는 도시를 검증한 뒤 Google Places에서 관광지 후보를 조회한다.

### Response

```json
{
  "places": [
    {
      "googlePlaceId": "ChIJ...",
      "name": "센소지",
      "formattedAddress": "일본 도쿄도 다이토구",
      "latitude": 35.7148,
      "longitude": 139.7967,
      "required": false
    }
  ]
}
```

취향 입력이 없는 일반 관광지 추천은 Google Text Search의 관련도 순서를 사용한다. Spring Backend는 필수 장소 보존, Google Place ID 중복 제거, 허용 장소 유형, 도시 범위와 결과 개수를 검증하며 임의의 자체 관심사 점수를 만들지 않는다. 외부 검색 결과의 순서는 시간이 지나며 달라질 수 있음을 허용한다.

---

# 6. 경로 최적화 API

## POST `/api/routes/optimize`

알고리즘 검증 및 독립 테스트를 위한 API.

### Request

```json
{
  "startGooglePlaceId": "ChIJ...hotel",
  "googlePlaceIds": ["ChIJ...1", "ChIJ...2"]
}
```

### Response

```json
{
  "route": [
    {
      "order": 1,
      "googlePlaceId": "ChIJ...hotel"
    },
    {
      "order": 2,
      "googlePlaceId": "ChIJ...2"
    },
    {
      "order": 3,
      "googlePlaceId": "ChIJ...1"
    },
    {
      "order": 4,
      "googlePlaceId": "ChIJ...hotel"
    }
  ],
  "totalDistanceKm": 25.3,
  "algorithm": "NEAREST_NEIGHBOR"
}
```

`startGooglePlaceId`는 경로의 시작과 종료 지점이다. `googlePlaceIds`의 각 장소는 중복 없이 한 번씩 방문하며, 시작점은 방문 목록에 포함할 수 없다. 서버는 Google Places로 장소와 좌표를 검증한 뒤 경로 알고리즘에 전달한다. `totalDistanceKm`은 시작점에서 첫 방문 장소까지, 방문 장소 사이, 마지막 방문 장소에서 시작점으로 돌아오는 모든 구간의 합이다.

향후 2-opt를 적용하면 알고리즘 이름과 개선 전후 거리를 비교할 수 있다.

---

# 7. 호텔 추천 API

## POST `/api/recommendations/hotels`

### Request

```json
{
  "cityGooglePlaceId": "ChIJ...city",
  "googlePlaceIds": ["ChIJ...1", "ChIJ...2"]
}
```

### Response

```json
{
  "hotels": [
    {
      "googlePlaceId": "ChIJ...hotel",
      "name": "Example Hotel",
      "totalDistanceKm": 15.4,
      "rank": 1
    }
  ]
}
```

MVP에서는 이동 거리 기준을 우선한다.

응답은 점수가 낮은 순서의 여러 호텔 후보를 반환한다. 클라이언트는 추천 후보 또는 선택 도시 안에서 직접 검색한 호텔의 Google Place ID를 최종 여행 계획 생성 요청에 전달할 수 있다.

---

# 8. 음식점 추천 API

## POST `/api/recommendations/restaurants`

저장된 여행 계획의 식사 슬롯에 맞는 음식점 후보를 조회한다. URL은 유지하되 저장 일정 조회를 조합하므로 Controller와 유스케이스 Service는 `travelplan`이 소유하고, 후보 계산은 `recommendation` Service에 위임한다.

### Request

```json
{
  "travelPlanId": 1,
  "date": "2026-10-01",
  "mealType": "LUNCH",
  "food": "돼지국밥"
}
```

네 필드는 모두 필수다. `travelPlanId`는 양의 정수, `date`는 목적지 현지 날짜 `yyyy-MM-dd`, `mealType`은 `LUNCH` 또는 `DINNER`, `food`는 앞뒤 공백 제거 후 1~50자다. 음식은 이번 검색 조건이며 저장된 음식 선호에 없어도 검색할 수 있고, 검색으로 선호 목록을 수정하지 않는다.

서버는 해당 계획의 날짜와 식사 슬롯을 조회하여 도시·호텔·하루 범위·방문 순서·도착/출발 시각·확정 식사 시각을 확보한다. 클라이언트가 `cityGooglePlaceId`, `routeStops`, 하루 범위나 식사 시각을 덮어쓰는 입력은 허용하지 않으며 `400 VALIDATION_FAILED`로 처리한다.

| 상황 | 응답 |
|---|---|
| 필수값·형식·음식 길이·열거값 오류 | `400 VALIDATION_FAILED` |
| 계획 없음 | `404 TRAVEL_PLAN_NOT_FOUND` |
| 날짜가 해당 계획 기간 밖임 | `400 INVALID_TRAVEL_DATE` |
| 해당 날짜에 요청한 식사 슬롯 없음 | `404 MEAL_SLOT_NOT_FOUND` |
| 정상 검색이지만 조건에 맞는 후보 없음 | `200`, `restaurants: []` |
| 참조 장소가 Google에서 조회되지 않음 | `404 PLACE_NOT_FOUND` |
| Google Places/Routes 장애 | `503 PLACE_PROVIDER_UNAVAILABLE` / `503 ROUTE_PROVIDER_UNAVAILABLE` |

계획·날짜·슬롯 검증을 마친 뒤에만 외부 검색을 수행한다. 없는 슬롯을 새로 만들거나 시간표를 재계산하지 않는다.

### Response

```json
{
  "mealType": "LUNCH",
  "travelPlanId": 1,
  "date": "2026-10-01",
  "food": "돼지국밥",
  "startTime": "12:00",
  "endTime": "13:00",
  "windowStartTime": "11:30",
  "windowEndTime": "14:00",
  "recommendationMode": "ROUTE_SEGMENT",
  "anchorGooglePlaceId": null,
  "restaurants": [
    {
      "googlePlaceId": "ChIJ...restaurant",
      "name": "OO돼지국밥",
      "foodMatched": true,
      "locationRelation": "NEARBY",
      "estimatedMealStartTime": "12:00",
      "detourDurationMinutes": 12,
      "detourDistanceKm": 0.8,
      "rank": 1
    }
  ]
}
```

`startTime`·`endTime`은 조회 시점에 저장된 식사 시각이다. 후보는 이 슬롯과 주변 일정의 시간을 지킬 수 있어야 하며, 맞는 후보가 없으면 빈 목록을 반환한다. 허용 시간대는 점심 `11:30~14:00`, 저녁 `17:30~20:30`이다. 일반 구간의 이탈 비용은 `A → 음식점 → B`의 이동 시간·거리에서 `A → B`를 뺀 증가분이다. 시간 충족 여부는 증가분만이 아니라 각 방향 이동시간과 저장된 시각으로 검증한다. `estimatedMealStartTime`은 저장된 `startTime`과 같아야 한다.

한 장소의 체류 구간이 요청한 식사 시간대 전체를 포함하면 `recommendationMode`는 `INSIDE_OR_NEARBY`, `anchorGooglePlaceId`는 그 장소가 된다. Google의 포함 관계로 확인된 내부 후보를 먼저 정렬하고, 없으면 Nearby Search의 인접 후보를 이동 시간 순으로 정렬한다. 확인되지 않은 후보를 `INSIDE`로 표시하지 않는다.

긴 체류에서는 식사와 음식점 왕복 이동을 기존 체류 시간 안에 포함한다. 고정 식사 시작까지 도착하고 장소의 저장된 출발 시각까지 복귀할 수 있는 후보만 허용한다. 이 모드의 `detourDurationMinutes`·`detourDistanceKm`은 기준 장소에서 음식점까지 왕복 이동의 합이며 식사 60분은 제외한다. 일반 구간에서는 직전 장소 출발·다음 장소 도착 시각을 유지할 수 있어야 한다. 상세 경계식과 중복 합산 방지는 `01-requirements.md` FR-11을 따른다. 내부 후보 우선 규칙도 시간 조건을 통과한 후보에만 적용한다.

---

# 9. TravelPlan API

이 절의 공개 CRUD API는 T2에서 연결한다. T1에서는 계산 완료된 일정의 내부 저장·조회·교체·삭제 Service를 테스트하며, 중간 생성 API나 클라이언트가 계산 결과를 직접 저장하는 API를 추가하지 않는다. 내부 전달 DTO는 아래 HTTP Request/Response 계약과 구분한다. 조회·수정·삭제의 상세 계약은 T2-06에서 확정한 뒤 T2-07에서 구현한다.

## POST `/api/travel-plans`

최종 핵심 API.

### Request

```json
{
  "cityGooglePlaceId": "ChIJ...city",
  "startDate": "2026-10-01",
  "endDate": "2026-10-03",
  "requiredGooglePlaceIds": ["ChIJ...1", "ChIJ...2"],
  "selectedRecommendedGooglePlaceIds": ["ChIJ...5", "ChIJ...8"],
  "selectedHotelGooglePlaceId": "ChIJ...hotel",
  "dailyStartTime": "10:00",
  "dailyEndTime": "21:00",
  "stayDurationOverrides": [
    {
      "googlePlaceId": "ChIJ...5",
      "stayMinutes": 240
    }
  ],
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
| 선택 도시 | Google Places에서 도시로 확인돼야 함 | `INVALID_DESTINATION_SELECTION` |
| 선택 호텔 | 필수. Google Places에서 숙박 장소로 확인되고 선택 도시 범위와 일치 | `INVALID_HOTEL_SELECTION` |
| 일일 장소 수 | 관광 장소 최대 6개 | `PLAN_CAPACITY_EXCEEDED` |
| 전체 장소 수 | 여행 일수 × 6 이하 | `PLAN_CAPACITY_EXCEEDED` |
| 하루 시간 | 생략 시 현지 시각 `10:00~21:00`, 지정 시 `HH:mm`이며 시작 < 종료 | `INVALID_DAILY_TIME_RANGE` |
| 체류 시간 수정 | 선택 장소에만 지정, Google Place ID 중복 없음, 양의 분 값이며 하루 시간 범위 이하 | `INVALID_STAY_DURATION` |
| 음식 목록 | 중복 없이 최대 1~5개, 각 값 1~50자 | `VALIDATION_FAILED` |

#### Google Place ID 무결성 검증

| 검증 | 실패 응답 |
|---|---|
| 선택 도시가 존재하지 않음 | `404 PLACE_NOT_FOUND` |
| 선택 도시가 도시 유형이 아님 | `400 INVALID_DESTINATION_SELECTION` |
| 방문 장소가 존재하지 않음 | `404 PLACE_NOT_FOUND` |
| 방문 장소가 관광 유형이 아니거나 선택 도시 소속이 아님 | `400 INVALID_PLACE_SELECTION` |
| 호텔이 존재하지 않음 | `404 PLACE_NOT_FOUND` |
| 호텔이 숙박 유형이 아니거나 선택 도시 소속이 아님 | `400 INVALID_HOTEL_SELECTION` |
| 필수·선택 장소 내부 또는 두 목록 사이의 중복 | `400 INVALID_PLACE_SELECTION` |
| 도시·호텔·방문 장소 사이의 역할 중복 | `400 INVALID_PLACE_SELECTION` |
| 체류 시간 수정 ID가 최종 방문 장소에 없거나 수정 목록에서 중복 | `400 INVALID_STAY_DURATION` |
| Google Places 검증을 완료할 수 없음 | `503 PLACE_PROVIDER_UNAVAILABLE` |

도시 소속은 Google Places가 제공한 포함 행정구역 등 명시적인 근거로 확인한다. 근거가 없어 소속을 확정할 수 없는 장소는 일정에 포함하지 않는다.

일정은 장소 유형별 기본 체류 시간, 사용자 수정 체류 시간, Google Routes의 정적 이동 시간과 식사 슬롯을 이용해 일일 시간 용량 안에 배치한다. 긴 체류 안의 식사 60분은 체류에 포함하고, 체류 밖 식사만 별도로 확보한다. 관광 장소는 최대 6개이며, 각 날짜의 경로는 그 날짜에 배치된 장소만 대상으로 선택 호텔에서 출발해 같은 호텔로 돌아오도록 계산한다.

모든 시각은 목적지 현지 시각이다. MVP의 `assumptions`는 영업시간과 실시간 교통을 반영하지 않았음을 명시한다. 숙소는 모든 날짜의 출발·종료 기준점이지만 방문 순서에는 포함하지 않는다. 식사 슬롯의 종류·확정 시작·종료 시각은 DB에 저장한다. 음식점 후보는 별도 검색 요청으로만 반환하며 일정에 자동 확정하거나 DB에 저장하지 않는다. 생성·조회·수정 응답에는 음식점 후보 필드를 포함하지 않는다.

### 처리 흐름

```text
입력 Validation
 ↓
요청 목록 교차 중복·체류 시간 수정 대상 검증
 ↓
선택 도시 존재·유형 검증
 ↓
방문 장소 존재·유형·도시 소속 검증
 ↓
선택 호텔 존재·유형·도시 소속 검증
 ↓
장소 유형별 체류 시간 결정
 ↓
Google Routes 정적 이동 시간 조회
 ↓
날짜별 경로·시간 용량 배치
 ↓
점심·저녁 슬롯의 시작·종료 시각 확정
 ↓
DB Transaction 시작
 ↓
Place 참조와 식사 슬롯을 포함한 TravelPlan Aggregate 전체 저장
 ↓
Response 반환
```

### Response 예시

```json
{
  "travelPlanId": 1,
  "title": "부산 2박 3일 여행",
  "city": {
    "googlePlaceId": "ChIJ...city",
    "name": "부산",
    "countryCode": "KR"
  },
  "hotel": {
    "googlePlaceId": "ChIJ...hotel",
    "name": "Example Hotel"
  },
  "assumptions": [
    "영업시간과 실시간 교통은 반영하지 않았습니다. 이동 시간은 Google Routes의 정적 값을 사용했습니다.",
    "음식점은 추천 후보이며 일정에 자동 확정되지 않습니다."
  ],
  "days": [
    {
      "day": 1,
      "date": "2026-10-01",
      "startTime": "10:00",
      "endTime": "21:00",
      "places": [
        {
          "order": 1,
          "googlePlaceId": "ChIJ...1",
          "name": "해운대",
          "role": "ATTRACTION",
          "arrivalTime": "10:25",
          "departureTime": "11:55",
          "stayMinutes": 90
        }
      ],
      "mealSlots": [
        {
          "mealType": "LUNCH",
          "windowStartTime": "11:30",
          "windowEndTime": "14:00",
          "startTime": "12:00",
          "endTime": "13:00",
          "reservedMinutes": 60
        }
      ]
    }
  ]
}
```

---

## GET `/api/travel-plans/{travelPlanId}`

여행 계획 상세 조회. 성공 시 `200`이며 생성 응답과 같은 일정 구조를 사용한다. 위 예시는 일부 날짜·장소만 보여주며 실제 응답은 저장된 전체 날짜와 장소를 반환한다.

- 계획 ID·제목·날짜·하루 범위·Google Place ID 참조·방문 순서·체류 및 도착·출발 시각·식사 슬롯은 저장값을 반환한다. `days`는 날짜순, `places`는 방문 순서, `mealSlots`는 시작 시각순이다. 슬롯이 없는 날짜는 `mealSlots: []`를 반환한다.
- 장소명·주소 등 Google 표시 정보만 `PlaceService`로 현재 값을 조회한다. 이 때문에 표시 정보가 달라져도 저장된 순서와 시각은 변경하지 않는다. Routes 호출·시간표 재계산·음식점 후보 검색·DB 변경은 수행하지 않는다.
- `windowStartTime`·`windowEndTime`은 식사 허용 시간대이며 `startTime`·`endTime`이 저장된 실제 식사 시각이다. 허용 시간대는 현재 MVP 정책으로 표시한다.
- 계획이 없으면 `404 TRAVEL_PLAN_NOT_FOUND`다. Google 상세 조회 실패는 기존 공통 계약의 `404 PLACE_NOT_FOUND` 또는 `503 PLACE_PROVIDER_UNAVAILABLE`로 응답하고 저장된 계획은 유지한다. 부분 표시 응답은 현재 계약에 추가하지 않는다.

---

## PUT `/api/travel-plans/{travelPlanId}`

여행 계획 입력 조건을 교체한다. Request는 POST와 같은 필드·validation을 사용한다. 필수값 누락은 오류이며, 선택값 생략은 POST의 기본값을 적용한다. 생략한 필드를 기존 값으로 유지하는 부분 수정으로 해석하지 않는다. 클라이언트가 방문 순서·도착 시각·식사 슬롯을 직접 덮어쓰는 입력은 받지 않는다. 제목 직접 수정은 이 계약에 포함하지 않는다.

도시·여행 날짜·방문 장소(필수 표시 포함)·호텔·하루 시간·체류 시간 조건이 바뀌면 Google 검증 후 경로·시간표·식사 슬롯을 재계산한다. 재계산은 별도 Service에 위임하며, 모든 계산이 성공한 뒤 하나의 저장 트랜잭션에서 교체한다. 음식 목록만 바뀌면 시간표·식사 슬롯은 유지한다. 음식점 후보는 수정 요청 안에서 검색하지 않는다.

검증·외부 호출·재계산 실패 시 저장을 시작하지 않으며, DB 저장 중 실패하면 전체 rollback하여 기존 계획·식사 슬롯을 보존한다. 성공 시 `200`과 갱신된 일정 구조를 반환한다. 응답용 외부 표시 정보는 저장 전에 확보하여 저장 성공 뒤 Google 호출 실패로 수정 실패를 응답하지 않도록 한다. 없는 계획은 `404 TRAVEL_PLAN_NOT_FOUND`다.

---

## DELETE `/api/travel-plans/{travelPlanId}`

여행 계획과 하위 Day·방문 장소·음식 선호·식사 슬롯을 하나의 트랜잭션으로 삭제한다. 공유 Place는 유지하고 Google API는 호출하지 않는다. 성공 시 `204`, 없는 계획은 `404 TRAVEL_PLAN_NOT_FOUND`다.

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
1. GET /hello와 개발 기반       [현재 존재/준비]
2. Google 장소 검색·Place 참조와 공통 오류 응답
3. 거리 계산과 경로 최적화
4. TravelPlan 내부 저장·조회·교체·삭제 계층 (공개 API 없음)
5. 국가 기반 AI 도시 추천과 OpenAI client
6. 장소·호텔·음식점 추천
7. 최종 POST /api/travel-plans와 조회·수정·삭제 API 통합
8. 운영 profile과 health
9. Auth / User와 TravelPlan 소유권
```

세부 작업 ID와 현재 작업은 `docs/11-command-roadmap.md`를 단일 기준으로 사용한다. 개별 기능 API를 먼저 만들고 충분히 테스트한 다음 최종 `POST /api/travel-plans`에서 조합한다.

---

# 12. 엔드포인트별 상태 코드

| 엔드포인트 | 성공 | 주요 실패 |
|---|---|---|
| `POST /api/destinations/cities/resolve` | `200` | `400`, `404`, `503` |
| `GET /api/places/google/{googlePlaceId}` | `200` | `400`, `404`, `503` |
| `POST /api/destinations/cities/recommend` | `200` | `400`, `404`, `503` |
| `POST /api/recommendations/*` | `200` | `400`, `404`, `503` |
| `POST /api/routes/optimize` | `200` | `400`, `404` |
| `POST /api/travel-plans` | `201` | `400`, `404`, `409`, `503` |
| `GET/PUT/DELETE /api/travel-plans/{travelPlanId}` | `200`/`200`/`204` | `400`, `404`, `409`, `503`(Google 상세 조회 필요 시) |
