# REST API 명세

## 1. 목적과 공통 규칙

Base Path는 /api이며 JSON을 사용한다. Entity와 외부 제공자 응답을 직접 반환하지 않고 Request·Response DTO를 사용한다.

이 문서는 목표 HTTP 계약이다. 구현 완료 상태는 docs/07-implementation-readiness.md에서 구분한다.

### 인증

- 공개 API는 `POST /api/users`, `POST /api/auth/login`, `GET /api/shared/travel-plans/{shareToken}`다. 그 밖의 `/api/**`는 Bearer JWT가 필요하다.
- 인증 사용자 ID는 토큰에서 얻으며 Request body의 userId를 신뢰하지 않는다.
- 자신의 일정만 조회·수정·삭제할 수 있다.
- 공유 조회는 별도 읽기 전용 토큰을 사용한다.

### 성공 상태

| 상황 | 상태 |
|---|---|
| 조회·계산·수정 | 200 |
| 리소스 생성 | 201 |
| 삭제 | 204 |

### 오류 형식

~~~json
{
  "code": "VALIDATION_FAILED",
  "message": "요청 값이 올바르지 않습니다.",
  "fieldErrors": [
    {"field": "title", "reason": "REQUIRED"}
  ],
  "details": null,
  "adjustments": [],
  "retryAfterSeconds": null
}
~~~

- 모든 오류는 위 여섯 필드를 같은 JSON 타입으로 반환한다. 값이 없을 때 `fieldErrors`와 `adjustments`는 빈 배열, `details`와 `retryAfterSeconds`는 `null`이며 필드를 생략하지 않는다. JSON 객체의 필드 순서는 계약이 아니다.
- `code`는 클라이언트 분기용 안정적인 대문자 snake case 값이다. `message`는 사용자에게 표시 가능한 안전한 한국어 문장이며 exception message, Bean Validation 기본 문구와 외부 제공자 문구를 그대로 반환하지 않는다.
- `fieldErrors` 원소는 JSON 필드 경로 `field`와 안정적인 `reason`으로 구성한다. 배열 원소는 `places[0].stayMinutes`처럼 표현하며 `field`, `reason` 오름차순으로 정렬하고 중복을 제거한다.
- 공통 `reason`은 `REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `INVALID_SIZE`, `DUPLICATE`, `INVALID_COMBINATION`이다. 이 목록으로 표현할 수 없는 객체 단위 검증은 `fieldErrors`를 비우고 공통 message만 반환한다.
- `details`는 오류 코드별로 명세된 구조화 DTO가 있을 때만 객체로 채운다. 자유 문자열, stack trace와 임의의 exception 속성은 넣지 않는다.
- `adjustments`는 오류 코드별로 명세된 조정 코드만 담고 중복을 제거한 명세 순서를 유지한다.
- `retryAfterSeconds`는 429에서만 1 이상의 정수로 채우고 HTTP `Retry-After` 헤더에도 같은 초 값을 반환한다.
- API 키, 좌표, 자연어 원문, 외부 요청·응답 원문을 오류에 넣지 않는다.

| 검증 원인 | reason |
|---|---|
| `NotNull`, `NotBlank`, `NotEmpty`, 필수 request parameter·header 누락 | `REQUIRED` |
| JSON 파싱, enum·날짜·시각·UUID·숫자 타입 변환, `Pattern`, `Email` | `INVALID_FORMAT` |
| `Min`, `Max`, `Positive`, `PositiveOrZero`, 허용 범위 밖 값 | `OUT_OF_RANGE` |
| `Size`로 제한한 문자열·배열 길이 | `INVALID_SIZE` |
| 중복 금지 배열·날짜·순서 | `DUPLICATE` |
| 여러 필드의 조합 규칙 위반 | `INVALID_COMBINATION` |

JSON 파싱처럼 신뢰할 수 있는 필드 경로를 얻을 수 없는 오류는 `fieldErrors`를 비운다. 하나의 필드에 여러 제약이 동시에 실패하면 각각의 reason을 정렬해 반환한다.

| 변환 대상 | 상태 | 코드 | 응답 규칙 |
|---|---:|---|---|
| DTO·메서드 validation, 잘못된 JSON·타입·필수 header | 400 | `VALIDATION_FAILED` | 필드 검증이면 `fieldErrors`, 그 밖에는 빈 배열 |
| Bearer token 없음·잘못됨·만료, 로그인 인증 실패 | 401 | `AUTHENTICATION_REQUIRED` | 원인을 구분하지 않는 같은 message, `WWW-Authenticate: Bearer` |
| 인증됐지만 다른 사용자의 자원 또는 권한 없음 | 403 | `ACCESS_DENIED` | 대상 소유자와 권한 상세 비노출 |
| 존재하지 않거나 공개적으로 숨겨야 하는 자원 | 404 | endpoint별 not-found 코드 | 현재 일정은 `TRAVEL_PLAN_NOT_FOUND`; 공유 토큰의 무효·만료도 같은 일정 not-found 응답 |
| 매핑되지 않은 URL·정적 리소스 | 404 | `RESOURCE_NOT_FOUND` | 요청 경로와 Spring 내부 오류 비노출 |
| 이미 사용 중인 회원가입 이메일 | 409 | `EMAIL_ALREADY_EXISTS` | 이메일과 기존 사용자 정보 비노출 |
| 같은 requestId가 처리 중·이미 성공 | 409 | `REQUEST_IN_PROGRESS`, `REQUEST_ALREADY_COMPLETED` | `request_executions` 상태에 따라 구분, 결과 ID 비노출 |
| 유효한 요청이지만 경로 없음·일정 시간 초과 | 422 | `ROUTE_NOT_FOUND`, `PLAN_CAPACITY_EXCEEDED` | 명세된 경우에만 `details`·`adjustments` 제공 |
| 사용자 또는 서비스 호출 한도 초과 | 429 | `RATE_LIMIT_EXCEEDED` | `retryAfterSeconds`와 `Retry-After` 필수 |
| 장소·경로·AI 제공자 기술 장애 | 503 | `PLACE_PROVIDER_UNAVAILABLE`, `ROUTE_PROVIDER_UNAVAILABLE`, `AI_UNAVAILABLE` | provider 상태·URL·payload 비노출 |
| AI 응답 schema·허용 목록 계약 위반 | 503 | `AI_RESPONSE_INVALID` | AI 원문과 validation 상세 비노출 |
| 예상하지 못한 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` | 고정된 일반 message만 반환 |

F0-04B Exception Handler는 Spring/Jackson validation 예외만 400으로 변환하고, 비즈니스 예외는 자신이 가진 공통 오류 code와 상태로 변환한다. 이미 응답이 시작된 오류를 다시 쓰지 않으며 예상하지 못한 예외는 500으로 변환한다. 같은 원인에 Controller별 handler를 두지 않고 전역 handler 하나를 사용한다.

오류별 기본 message는 서버에서 code와 함께 관리한다. 사용자 입력값을 message에 이어 붙이지 않는다. `PLAN_CAPACITY_EXCEEDED`처럼 명세가 허용한 날짜·시각·분 값은 문자열 조합 대신 `details` DTO로 전달하며 message는 `일정이 허용 시간을 초과합니다.`로 고정한다.

| code | 기본 message |
|---|---|
| `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. |
| `AUTHENTICATION_REQUIRED` | 인증이 필요합니다. |
| `ACCESS_DENIED` | 접근 권한이 없습니다. |
| `RESOURCE_NOT_FOUND` | 요청한 리소스를 찾을 수 없습니다. |
| `TRAVEL_PLAN_NOT_FOUND` | 일정을 찾을 수 없습니다. |
| `EMAIL_ALREADY_EXISTS` | 이미 사용 중인 이메일입니다. |
| `REQUEST_IN_PROGRESS` | 같은 요청을 처리 중입니다. |
| `REQUEST_ALREADY_COMPLETED` | 이미 처리된 요청입니다. |
| `ROUTE_NOT_FOUND` | 이동 경로를 찾을 수 없습니다. |
| `PLAN_CAPACITY_EXCEEDED` | 일정이 허용 시간을 초과합니다. |
| `RATE_LIMIT_EXCEEDED` | 호출 한도를 초과했습니다. |
| `PLACE_PROVIDER_UNAVAILABLE` | 장소 검색 서비스를 일시적으로 사용할 수 없습니다. |
| `ROUTE_PROVIDER_UNAVAILABLE` | 경로 서비스를 일시적으로 사용할 수 없습니다. |
| `AI_UNAVAILABLE` | AI 서비스를 일시적으로 사용할 수 없습니다. |
| `AI_RESPONSE_INVALID` | AI 응답을 처리할 수 없습니다. |
| `INTERNAL_SERVER_ERROR` | 서버 오류가 발생했습니다. |

429의 `retryAfterSeconds`는 초 단위 현재 시각에서 적용된 분 또는 일 한도 창이 끝날 때까지 남은 시간을 올림하고 최소 1로 계산한다. 여러 한도가 동시에 막으면 가장 오래 기다려야 하는 값을 사용한다. 일 한도 종료는 `Asia/Seoul`의 다음 자정이다.

날짜는 yyyy-MM-dd, 시각은 HH:mm 형식이다. ID는 양의 정수이며 배열 입력은 별도 허용이 없으면 중복을 거부한다.

### 중복 요청

외부 API나 AI를 호출하는 POST에는 Idempotency-Key 헤더가 필수다.

- 값은 클라이언트가 생성한 UUID 형식 requestId다.
- 사용자·기능·requestId 조합과 처리 상태를 MySQL `request_executions`에 10분간 유지한다.
- 처리 중 같은 요청은 409 REQUEST_IN_PROGRESS를 반환한다.
- 성공한 같은 요청은 409 REQUEST_ALREADY_COMPLETED를 반환한다.
- 두 중복 응답 모두 외부 API와 저장 로직을 다시 실행하거나 호출량을 추가 차감하지 않는다. 10분 수명이 끝난 requestId는 새 요청으로 처리할 수 있다.
- 결과 리소스 ID와 요청·응답 payload는 중복 요청 저장소에 보관하지 않는다. 클라이언트는 완료 상태를 받은 뒤 필요한 자원을 별도 조회 API로 확인한다.
- 사용자·서비스 호출량은 MySQL `api_usage_counters`에서 공유하며 조건부 갱신으로 외부 호출 전에 원자적으로 확보한다.
- 외부 호출이 실행된 뒤 실패하면 호출량에는 포함한다.

---

## 2. 인증 API

### POST /api/users

회원가입.

~~~json
{
  "email": "user@example.com",
  "password": "plain request only"
}
~~~

성공 시 body 없이 201을 반환한다. 이메일은 소문자로 정규화해 저장한다. 이미 사용 중인 이메일은 409 `EMAIL_ALREADY_EXISTS`로 반환한다. 비밀번호는 Response나 로그에 포함하지 않고 해시만 DB에 저장한다.

### POST /api/auth/login

~~~json
{
  "email": "user@example.com",
  "password": "plain request only"
}
~~~

~~~json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600
}
~~~

비밀번호는 8~64 Unicode code point, UTF-8 기준 72바이트 이하이며 제어 문자를 허용하지 않는다. 서버는 앞뒤 공백 제거 또는 Unicode 정규화 없이 입력 그대로 검증·해시한다. 길이·바이트 수 위반은 `password`의 `INVALID_SIZE`, 제어 문자는 `INVALID_FORMAT`으로 반환한다. 비밀번호 문자 종류 조합 규칙은 두지 않는다.

access JWT는 발급 후 1시간 유효하다. refresh token과 로그아웃 endpoint는 MVP에서 제공하지 않으며 브라우저는 token을 메모리에서 제거해 로그아웃한다. 서버는 보호 API마다 JWT와 token의 User 존재 여부를 확인하므로 탈퇴한 사용자의 기존 token도 401로 거부한다.

로그인 자격 증명 불일치, 존재하지 않는 이메일과 보호 API의 누락·잘못된·만료 Bearer token은 모두 401 `AUTHENTICATION_REQUIRED`의 같은 message를 사용한다. 이메일 존재 여부와 token 실패 원인을 Response에서 구분하지 않는다. 인증은 성공했지만 소유권 또는 권한이 없으면 403 `ACCESS_DENIED`다.

### DELETE /api/users/me

인증된 현재 사용자의 계정, 소유 TravelPlan Aggregate와 사용자 범위 호출 카운터·requestId 처리 행을 한 번의 짧은 DB 트랜잭션으로 영구 삭제한다. 외부 API는 호출하지 않는다. 성공 시 204를 반환하며 복구·유예 기간은 없다. 다른 사용자를 지정하는 path 또는 request body의 userId는 받지 않는다.

---

## 3. 지역 API

### GET /api/regions?query=강릉

정적 regions.json에서 서울특별시·광역시·세종특별자치시, 도·특별자치도와 그 아래 시·군, 특별시·광역시 아래 검색 필터용 구·군을 검색한다. 서울특별시·광역시·세종특별자치시는 자체를 최종 선택할 수 있고 도·특별자치도는 그 아래 시·군 하나만 최종 선택한다. 특별시·광역시 아래 구·군은 장소 검색 필터로만 반환하며 최종 일정의 regionId로 사용할 수 없다. 읍·면·동과 해외 지역은 반환하지 않는다.

~~~json
{
  "regions": [
    {
      "regionId": "KR-51150",
      "name": "강릉시",
      "shortName": "강릉",
      "provinceName": "강원특별자치도",
      "parentRegionId": "KR-51",
      "type": "CITY",
      "selectable": true,
      "placeSearchFilterable": false
    }
  ]
}
~~~

### GET /api/regions?query=해운대

~~~json
{
  "regions": [
    {
      "regionId": "KR-26350",
      "name": "해운대구",
      "shortName": "해운대",
      "provinceName": "부산광역시",
      "parentRegionId": "KR-26",
      "type": "DISTRICT_FILTER",
      "selectable": false,
      "placeSearchFilterable": true
    }
  ]
}
~~~

`parentRegionId`, `selectable`, `placeSearchFilterable`로 최종 여행 지역과 장소 검색 필터를 구분한다. 최상위 지역의 parentRegionId는 null이다. `placeSearchFilterable=true`인 항목은 특별시·광역시 아래 구·군이며 최종 지역으로 선택할 수 없다. 대표 좌표와 데이터 출처는 내부 검색 범위 계산에 사용하며 공개 Response의 필수 필드로 노출하지 않는다.

`regionId`는 Routy가 소유하는 불투명하고 안정적인 식별자다. 클라이언트는 `KR-` 접두사, 숫자 길이 또는 문자열 구조에서 지역의 계층·유형·외부 행정코드를 추론하지 않고 응답의 `parentRegionId`, `type`, `selectable`, `placeSearchFilterable`을 사용한다.

### POST /api/ai/regions/recommend

~~~json
{
  "request": "바다가 있고 조용한 곳에 가고 싶어"
}
~~~

- request는 trim 후 1~500자다.
- AI는 서버가 제공한 지역 목록에서 정확히 3개만 선택한다.
- 사용자당 분당 2회, 하루 10회다.

~~~json
{
  "regions": [
    {
      "regionId": "KR-51150",
      "name": "강릉시",
      "provinceName": "강원특별자치도",
      "reason": "바다와 도심 관광을 함께 선택할 수 있습니다."
    }
  ]
}
~~~

AI 응답에 허용 목록 밖의 ID·중복이 있거나 AI 응답 JSON을 해석할 수 없으면 503 `AI_RESPONSE_INVALID`다. 클라이언트 요청 JSON을 해석할 수 없는 경우는 400 `VALIDATION_FAILED`다. 요청 자연어와 AI 원문은 저장하지 않는다.

---

## 4. 메뉴 분석 API

### POST /api/ai/menus/analyze

~~~json
{
  "regionId": "KR-26",
  "request": "해운대에서 부산다운 음식과 매운 음식을 먹고 싶어",
  "attractions": [
    {"clientPlaceId": "browser-uuid", "displayName": "해운대 해수욕장"}
  ]
}
~~~

- request는 trim 후 1~200자다.
- regionId는 trim하거나 구조를 해석하지 않으며 최종 선택 가능한 국내 지역 ID와 정확히 일치해야 한다.
- attractions는 0~35개다. clientPlaceId는 공백 없는 1~100자이며 요청 안에서 중복될 수 없고, displayName은 trim 후 1~50자다.
- 잘못된 regionId, 관광지 개수·필드·중복은 외부 호출과 호출량 차감 전에 400 `VALIDATION_FAILED`로 거절한다.
- 사용자당 분당 3회, 하루 15회다.
- 잘못된 구조화 응답은 최대 1회 재시도하며 재시도도 한도에 포함한다.

~~~json
{
  "menus": [
    {"name": "돼지국밥", "searchQuery": "돼지국밥", "reason": "부산 지역 음식", "targetClientPlaceId": "browser-uuid"},
    {"name": "낙곱새", "searchQuery": "낙곱새", "reason": "매운 음식 요청 반영", "targetClientPlaceId": null}
  ]
}
~~~

AI는 지역·관광지 맥락과 자연어 요청에서 중복 없는 메뉴 1~5개, 카카오 검색어, 짧은 이유와 선택적인 대상 관광지를 구조화한다. 식당 선택·평가와 동선 순위는 결정하지 않는다. 사용자가 결과를 수정·삭제·확정하며 두 번 실패하면 화면은 직접 메뉴 입력을 제공한다. AI 요청·응답 원문, 이유와 대상 연결은 저장하지 않는다.

---

## 5. 카카오 장소 검색 API

### POST /api/places/search

~~~json
{
  "regionId": "KR-26",
  "districtFilterId": "KR-26350",
  "placeRole": "ATTRACTION",
  "query": "해변",
  "center": null,
  "radiusMeters": 20000,
  "page": 1,
  "size": 15
}
~~~

- 이 endpoint의 placeRole은 ATTRACTION만 허용한다. HOTEL은 `/api/places/hotels/search`, RESTAURANT는 `/api/places/restaurants/search`의 검증된 제작 흐름에서만 검색한다.
- size는 1~15다.
- regionId는 최종 선택 가능한 서울특별시·광역시·세종특별자치시 또는 도·특별자치도 아래 시·군 하나다.
- districtFilterId는 ATTRACTION 검색에서만 사용하는 선택 필드다. 특별시·광역시 아래 `placeSearchFilterable=true`인 구·군 하나만 허용하고 상위 지역이 regionId와 일치해야 한다.
- 도·특별자치도 아래 시·군과 세종특별자치시는 districtFilterId를 받을 수 없다. 선택 불가능한 항목, 다른 상위 지역의 구·군과 읍·면·동은 400 `VALIDATION_FAILED`다.
- 최초 검색 중심은 regionId의 공공데이터 대표 좌표다.
- 중심 이동 재검색에서만 center를 받을 수 있으며 이때 역할별 허용 radiusMeters가 필수다.
- center가 null이고 radiusMeters가 있으면 지역 대표 좌표를 중심으로 관광지를 20km 반경 검색한다.
- center와 radiusMeters가 모두 null인 요청은 사용자가 지역 전체 검색을 명시적으로 요청한 경우로 해석하고, 공식 지역명을 query에 결합해 서버의 searchBounds를 rect로 사용한다.
- 반경 결과가 적다는 이유만으로 서버가 지역 전체 검색으로 자동 전환하지 않는다. 지역 전체 검색은 사용자 요청 때만 모든 최종 선택 가능 지역에 미리 생성한 `searchBounds`를 카카오 `rect`로 사용하고 공식 지역명을 검색어에 포함한다. `rect` 직렬화 순서는 카카오의 `left X,left Y,right X,right Y`, 즉 `minLongitude,minLatitude,maxLongitude,maxLatitude`로 고정한다.
- `searchBounds`는 공공 행정구역 WGS84 경계 전체를 감싸는 최소 축 정렬 사각형이며 클라이언트 요청·응답에 노출하지 않는 서버 내부 기준 데이터다. `MAP_BOUNDS`처럼 사용자가 이동한 현재 지도 영역과는 다른 값이다.
- 반경·`searchBounds` 검색 모두 반환 주소가 선택 지역의 행정구역과 일치할 때만 결과로 사용한다. `searchBounds`가 주변 지역과 바다를 포함할 수 있으므로 사각형 포함 여부만으로 지역을 판정하지 않는다.
- 반경 검색과 `searchBounds` 검색에서 같은 카카오 장소 ID가 나오면 하나로 합친다. 각 페이지와 보완 검색은 실제 외부 호출 수에 포함하며, 카카오의 노출 가능 결과 상한 때문에 도시의 모든 장소를 빠짐없이 제공한다고 보장하지 않는다.
- districtFilterId가 있으면 반경·`searchBounds`·지도 영역 검색 모두에서 반환 주소가 해당 구·군에도 속해야 한다. 지도 이동만으로 필터를 자동 변경·해제하지 않으며 빈 결과는 200과 빈 배열이다.
- 음식점은 1→3→5km 정책 안에서 호출한다.
- 사용자당 분당 20회, 하루 300회다.
- 결과가 부족해도 임의 장소를 추가하지 않는다.

~~~json
{
  "places": [
    {
      "kakaoPlaceId": "26338954",
      "placeUrl": "https://place.map.kakao.com/26338954",
      "providerDisplayName": "검색 화면에서만 표시할 장소명",
      "address": "검색 화면에서만 표시할 주소",
      "latitude": 35.1587,
      "longitude": 129.1604,
      "suggestedStayMinutes": 90,
      "selectionToken": "short-lived-signed-token"
    }
  ],
  "page": 1,
  "hasNext": false
}
~~~

providerDisplayName, address, 좌표, 카테고리는 제작 화면에서만 사용하며 저장하지 않는다. 카테고리 원문은 Response에 노출하지 않고 서버가 suggestedStayMinutes로 즉시 변환한다.

selectionToken은 선택값 변조 방지를 위한 짧은 수명의 서명 토큰이다. 카카오 장소 ID·URL·좌표·검색 역할과 만료 시각을 무결성 보호하며 서버 저장소를 만들지 않는다. 브라우저는 메모리에만 보관하고 localStorage·sessionStorage·IndexedDB에 저장하지 않는다. 토큰 만료시간과 서명 방식은 보안 ADR에서 확정한다.

토큰에는 인증 사용자 ID를 서명 대상에 포함하고 현재 JWT 사용자와 일치하는지 검증한다. `clientPlaceId`는 브라우저가 생성하는 작성 항목 식별자이며 보안 식별자나 DB 저장값으로 신뢰하지 않는다.

이 토큰과 좌표 활용 계약은 2026-09-11 카카오 DevTalk 답변으로 허용 범위를 확인했다. 브라우저는 한 번의 작성 흐름 동안 메모리에만 유지하고, 서버는 estimate와 create 각각의 요청 지역 변수에서만 사용한 뒤 응답 전에 폐기한다.

### POST /api/places/hotels/search

선택 관광지의 `selectionToken` 목록과 검색 모드를 받아 제작 중 지도에 숙소 후보를 표시한다.

- `GEOMETRIC_MEDIAN`: 관광지의 기하 중앙값 주변 5km를 기본으로 검색하며 사용자가 요청하면 10km로 넓힌다.
- `MEDOID`: 다른 관광지까지 Haversine 거리 합이 가장 작은 실제 관광지 주변을 검색한다.
- `MAP_BOUNDS`: 사용자가 이동한 현재 지도 영역 안을 검색한다.
- 숙소 결과는 점수나 거리 합으로 추천 순위를 만들지 않는다.
- 관광지와 숙소 마커, 숙소 목록을 동기화하고 사용자가 하나를 선택한다.
- 관광지 검색의 districtFilterId를 숙소 검색에 자동 적용하지 않는다. 숙소 후보는 선택 관광지 기반 검색 중심과 사용자가 지정한 지도 영역으로 탐색한다.
- 좌표·주소·검색 원문은 지도 표시와 선택 지역 검증에만 사용하고 작성 흐름 종료 시 폐기한다.

---

## 6. 제작 중 일정 계산 API

### POST /api/travel-plans/estimate

실제 경로 API를 호출하지 않고 브라우저가 보유한 선택 장소로 Haversine 기반 방문 순서와 대략적인 시간표를 계산한다. DB와 서버 세션에 초안을 저장하지 않는다.

~~~json
{
  "regionId": "KR-26",
  "travelMode": "CAR",
  "startDate": "2026-10-01",
  "endDate": "2026-10-03",
  "days": [
    {
      "date": "2026-10-01",
      "activityStartTime": "10:00",
      "activityEndTime": "20:00"
    }
  ],
  "places": [
    {
      "clientPlaceId": "1b86ad0e-85a7-4aa1-9db9-c6c57dc4e141",
      "selectionToken": "signed-token",
      "displayName": "바닷가 산책",
      "stayMinutes": 90,
      "day": null,
      "order": null
    }
  ],
  "hotelSelectionToken": "signed-token-or-null",
  "mealTravelBufferMinutes": 15,
  "foods": ["돼지국밥", "낙곱새"]
}
~~~

검증:

- 여행 기간 1~7일
- mealTravelBufferMinutes는 한쪽 여유 기준 정수 0~60이며 생략하면 15다.
- foods는 사용자가 확정한 중복 없는 메뉴 1~5개이며 각 값은 trim 후 1~50자다.
- 모든 날짜 조건 포함, 시작 < 종료
- travelMode는 CAR 또는 PUBLIC_TRANSIT
- 표시 이름 trim 후 1~50자
- stayMinutes 30~480, 10의 배수
- 하루 관광지 최대 5개
- 1박 이상이면 호텔 필수, 당일치기는 null
- selectionToken의 서명·만료·역할 일치
- 모든 장소의 day·order가 모두 null이거나 모두 지정됐는지
- clientPlaceId가 요청 안에서 중복되지 않는 브라우저용 UUID인지

day와 order가 모두 null이면 서버가 날짜와 순서를 자동 추천한다. 모든 장소에 값이 있으면 사용자가 조정한 날짜와 순서를 보존하고 시간표만 다시 계산한다. 일부 장소에만 값이 있거나 날짜·순서가 중복·누락되면 400 `VALIDATION_FAILED`다.

Response는 날짜별 순서와 추정 시각을 반환한다. `routeVerified=false`는 아직 외부 경로 제공자에게 인접 구간을 조회하지 않았다는 뜻이다. 이후 외부 경로 조회가 성공하더라도 미래 여행일의 시간표·운행 여부나 실제 소요시간이 검증됐다는 뜻은 아니다.

~~~json
{
  "routeVerified": false,
  "days": [
    {
      "date": "2026-10-01",
      "items": [
        {
          "clientPlaceId": "1b86ad0e-85a7-4aa1-9db9-c6c57dc4e141",
          "order": 1,
          "type": "VISIT",
          "displayName": "바닷가 산책",
          "startTime": "10:00",
          "endTime": "11:30",
          "estimatedMinutes": null
        },
        {
          "order": 2,
          "type": "MOVE",
          "displayName": null,
          "startTime": "11:30",
          "endTime": "12:00",
          "estimatedMinutes": 30
        }
      ]
    }
  ]
}
~~~

이 API 결과는 저장 가능한 완료 일정이 아니다.

---

## 7. 일정 생성 API

### POST /api/travel-plans

사용자가 추정 시간표를 확인·조정한 뒤 호출한다. 최종 요청은 모든 장소의 day와 order를 포함한다. 서버는 날짜·순서의 완전성, 중복과 장소 집합을 다시 검증하되 사용자가 확정한 배치를 임의로 재정렬하지 않는다. 완성 후보의 인접 구간만 실제 경로로 검증하고 통과하면 하나의 트랜잭션으로 저장한다.

Request는 estimate 입력에 다음 필드를 추가한다.

~~~json
{
  "title": "부산 2박 3일",
  "regionId": "KR-26",
  "travelMode": "CAR",
  "startDate": "2026-10-01",
  "endDate": "2026-10-03",
  "days": [],
  "places": [],
  "hotelSelectionToken": "signed-token",
  "meals": [
    {
      "date": "2026-10-01",
      "mealType": "LUNCH",
      "restaurantSelectionToken": null,
      "displayName": "점심 식사",
      "memo": null
    }
  ]
}
~~~

- title은 trim 후 1~100자다.
- mealType은 LUNCH 또는 DINNER다.
- 식사시간은 60분이다.
- restaurantSelectionToken은 식당을 선택했을 때만 전달한다.
- 표시 이름·메모는 사용자 입력이며 제공자 장소명 자동 복사값이 아니다.
- 사용자는 일정 전체에서 자동차 또는 대중교통 하나만 선택한다.

처리:

1. 인증·호출 한도·중복 요청 검증
2. DTO와 selectionToken 검증
3. 사용자가 확정한 날짜·순서와 장소 집합 검증
4. 인접 구간 실제 경로 조회
5. 응답 초를 10분 단위로 올림
6. 체류·식사·이동시간으로 날짜별 용량 검증
7. 저장 금지 필드 제거
8. DB 트랜잭션 시작
9. TravelPlan Aggregate 전체 저장
10. 201과 Location 헤더 반환

시간을 초과하면 저장하지 않고 422를 반환한다.

~~~json
{
  "code": "PLAN_CAPACITY_EXCEEDED",
  "message": "일정이 허용 시간을 초과합니다.",
  "fieldErrors": [],
  "details": {
    "date": "2026-10-02",
    "plannedEndTime": "20:30",
    "allowedEndTime": "20:00",
    "exceededMinutes": 30
  },
  "adjustments": [
    "CHANGE_END_TIME",
    "CHANGE_STAY_MINUTES",
    "REMOVE_PLACE",
    "EXCLUDE_MEAL",
    "CHANGE_ORDER"
  ],
  "retryAfterSeconds": null
}
~~~

서버는 장소를 자동 삭제하거나 체류시간을 줄이지 않는다.

기술 장애는 1회 재시도 후 거리 기반 추정시간으로 대체할 수 있으며 성공 Response의 warnings에 표시한다. 정상적인 경로 없음은 대체하지 않고 422 ROUTE_NOT_FOUND다. 완료 생성 전에 실제 제공자 요청 수를 계산해 사용자와 서비스 잔여 한도를 원자적으로 확보하며, 확보하지 못하면 외부 경로 API를 호출하지 않고 전체 구간을 Haversine 기반 예상시간으로 계산해 warning을 반환한다.

카카오모빌리티 자동차 길찾기의 HTTP 200 응답에서 `result_code=1`인 경우는 선택한 자동차 이동수단으로 제공자가 유효한 경로를 반환하지 못한 정상 결과로 해석한다. 물리적으로 길이 없거나 도보·선박만 가능하다고 원인을 단정하지 않으며 `RouteResult.NotFound`로 변환한다. 재시도·Haversine fallback·자동 장소 삭제·자동 이동수단 변경 없이 일정 전체를 저장하지 않고 422 `ROUTE_NOT_FOUND`를 반환한다. 브라우저는 작성 상태를 유지하고 사용자가 순서·장소 또는 이동수단을 변경한 뒤 전체 경로 검증을 다시 요청한다. 이동수단 변경은 기존 요청 안에서 혼합하거나 서버가 자동 수행하지 않는다.

Routy의 자동차 Client는 인접한 두 지점을 `origin`과 `destination`으로만 보내고 카카오 `waypoints` 파라미터를 사용하지 않는다. 따라서 경유지 주변 도로 탐색 실패인 `result_code=101`과 경유지 주변 교통 장애인 `result_code=107`은 정상적인 Routy 요청에서 예상할 수 없는 응답이다. 두 코드는 `RouteClientFailure.INVALID_RESPONSE`로 변환하고 재시도·Haversine fallback 없이 503 `ROUTE_PROVIDER_UNAVAILABLE`을 반환하며 일정을 저장하지 않는다. 이를 사용자의 장소·순서 문제인 `ROUTE_NOT_FOUND`로 바꾸지 않는다. 제공자 `result_message`, 경유지 번호, 좌표와 요청 payload는 오류 응답과 로그에 남기지 않으며, 원문 없는 provider·result code metric만 허용한다. 향후 `waypoints`를 도입하려면 별도 Task와 공식 계약 재검토가 필요하다.

시작 지점 주변 도로를 탐색할 수 없는 `result_code=102`와 도착 지점 주변 도로를 탐색할 수 없는 `103`은 유효한 인접 구간 요청에서 발생할 수 있는 정상적인 경로 없음으로 보고 `RouteResult.NotFound`로 변환한다. 요청 형식 오류로 취급하지 않으며 `result_code=1`과 동일하게 재시도·Haversine fallback 없이 422 `ROUTE_NOT_FOUND`로 저장을 차단한다. 제공자가 주변 자동차 도로에 연결하지 못했다는 결과 이상으로 물리적 도로 부재, 도보·선박 필요 또는 좌표 자체의 오류를 단정하지 않는다.

출발지와 도착지가 5m 이내여서 경로를 탐색하지 않는 `result_code=104`는 실패가 아니라 이동 경로가 필요 없는 성공인 `RouteResult.Found(0)`으로 변환한다. 재시도·fallback·422·503 없이 생성을 계속하며 해당 `MOVE.estimatedMinutes`는 0이다. 0분에는 10분 단위 올림이나 고정 이동 buffer를 적용하지 않는다. 서버가 Haversine 거리로 카카오의 내부 5m 판정을 미리 복제해 호출을 생략하지 않고, 실제 제공자 응답이 104일 때만 이 규칙을 적용한다.

시작 지점 주변 도로에 교통 장애가 있는 `result_code=105`와 도착 지점 주변 도로에 교통 장애가 있는 `106`은 현재 선택한 자동차 구간의 정상적인 경로 없음인 `RouteResult.NotFound`로 변환한다. 제공자 자체 장애인 503으로 취급하지 않고 `result_code=1·102·103`과 동일하게 재시도·Haversine fallback 없이 422 `ROUTE_NOT_FOUND`로 저장을 차단한다. 오류 응답은 사고·통제 등 변할 수 있는 구체적 원인을 노출하지 않고 사용자가 장소·순서·이동수단을 조정하거나 나중에 전체 검증을 다시 요청하도록 한다.

카카오 대중교통의 `status=OK` 응답은 `routes[0].properties.totalTime`만 사용한다. 제공자 배열을 재정렬하거나 모든 후보의 최소시간·환승·요금·거리를 비교하지 않는다. 첫 후보의 초 단위 시간을 10분 단위로 올려 `MOVE.estimatedMinutes`에 반영하므로 34분은 40분, 정확히 40분은 40분이다. 공식 문서가 첫 후보를 추천·최단 경로로 보장하지 않는 한계는 수용한다. `routes`가 없거나 첫 후보의 `totalTime`이 누락 또는 음수이면 뒤 후보로 대체하지 않고 `RouteClientFailure.INVALID_RESPONSE`로 변환한다. 선택에 필요하지 않은 나머지 후보와 `steps`, 정류장·노선·좌표·요금·`landingURL`은 즉시 폐기하고 저장·로그·오류 응답에 남기지 않는다.

대중교통 요청에는 여행 날짜와 출발 시각을 전달할 수 없으므로 선택한 `totalTime`은 API 조회 시 제공자가 반환한 일정 계획용 예상 이동시간이다. 미래 여행일의 운행 여부·배차·막차·지연과 실제 소요시간을 검증하거나 보장하지 않는다. 제작·완료·공유 응답과 화면은 모두 `예상 이동시간`으로 표시하며 `실제 이동시간`, `확정 시간` 또는 미래 시간표 검증 완료로 표현하지 않는다. 완료·공유 조회 시 외부 API를 다시 호출하거나 여행 당일 자동 재계산하지 않는다.

대중교통의 `STARTNODES_NULL`, `ENDNODES_NULL`, `NO_RESULTS`는 유효한 인접 구간의 정상적인 경로 없음으로 `RouteResult.NotFound`로 변환한다. 재시도·Haversine fallback 없이 422 `ROUTE_NOT_FOUND`로 저장을 차단하고, `date + moveOrder + travelMode`만 반환한다. `EQUAL_POINTS`는 이동이 필요 없는 `RouteResult.Found(0)`으로 변환하며 10분 올림·고정 이동 buffer 없이 생성한다. `INVALID_REQUEST`는 사용자가 좌표를 직접 전달하지 않고 서버가 선택 token에서 요청을 구성하는 계약상 `RouteClientFailure.INVALID_REQUEST`로 변환한다. 문서에 없는 status, status 누락, `OK`인데 `routes`가 없거나 첫 후보가 손상된 응답은 `RouteClientFailure.INVALID_RESPONSE`로 변환한다. 두 failure는 재시도·fallback 없이 503 `ROUTE_PROVIDER_UNAVAILABLE`로 저장을 차단하며, 카카오 원문·좌표·payload는 오류 응답과 로그에 포함하지 않는다.

`ROUTE_NOT_FOUND`는 장소 식별자가 아니라 실패한 `MOVE` 항목의 날짜와 순서로 구간을 식별한다.

~~~json
{
  "code": "ROUTE_NOT_FOUND",
  "message": "이동 경로를 찾을 수 없습니다.",
  "fieldErrors": [],
  "details": {
    "date": "2026-10-01",
    "moveOrder": 4,
    "travelMode": "CAR"
  },
  "adjustments": [
    "CHANGE_ORDER",
    "REMOVE_PLACE",
    "CHANGE_TRAVEL_MODE"
  ],
  "retryAfterSeconds": null
}
~~~

- `date`는 경로를 찾지 못한 일정 날짜다.
- `moveOrder`는 서버가 최종 생성 후보에 부여한 해당 날짜 `items` 배열의 1부터 시작하는 `order`이며, 그 순서에 놓일 `MOVE` 하나를 가리킨다. 날짜 안에서 유일해야 한다.
- `travelMode`는 요청 전체에 적용된 `CAR` 또는 `PUBLIC_TRANSIT`다. 서버가 실패 뒤 다른 이동수단으로 자동 변경했다는 뜻이 아니다.
- 브라우저는 같은 날짜의 추정 일정에서 `moveOrder` 직전·직후 항목을 함께 강조한다. 일정 재계산으로 정확히 대응하는 항목을 찾을 수 없으면 날짜 단위 오류로 표시하고 임의의 장소를 지목하지 않는다.
- 이 방식은 출발·도착 경계, 관광지, 숙소와 식사 사이의 이동 및 같은 숙소의 반복 방문을 별도 장소 ID 없이 식별한다. 좌표·장소명·카카오 장소 ID·제공자 `result_msg`는 오류 응답에 포함하지 않는다.

이 오류 DTO와 전역 예외 매핑의 구현은 `T1-06A`, 브라우저 작성 상태 유지와 실패 구간 강조는 `W1-03B`에서 각각 수행한다. C1-05A에서는 문서 계약만 확정한다.

`warnings`는 현재 생성 응답에서만 제공하며 DB에 저장하지 않는다. MOVE에는 출처 구분 없이 10분 단위 `estimatedMinutes`만 저장하고, 이후 완료·공유 조회도 모든 값을 예상 이동시간으로 표시한다.

### 생성 Response

~~~json
{
  "travelPlanId": 1,
  "title": "부산 2박 3일",
  "region": {
    "regionId": "KR-26",
    "displayName": "부산광역시"
  },
  "travelMode": "CAR",
  "startDate": "2026-10-01",
  "endDate": "2026-10-03",
  "warnings": [],
  "days": [
    {
      "day": 1,
      "date": "2026-10-01",
      "activityStartTime": "10:00",
      "activityEndTime": "20:00",
      "items": [
        {
          "itemId": 10,
          "order": 1,
          "type": "VISIT",
          "planPlaceId": 20,
          "displayName": "바닷가 산책",
          "placeUrl": "https://place.map.kakao.com/...",
          "startTime": "10:00",
          "endTime": "11:30",
          "stayMinutes": 90,
          "estimatedMinutes": null,
          "memo": null
        },
        {
          "itemId": 11,
          "order": 2,
          "type": "MOVE",
          "planPlaceId": null,
          "displayName": null,
          "placeUrl": null,
          "startTime": "11:30",
          "endTime": "12:10",
          "stayMinutes": null,
          "estimatedMinutes": 40,
          "memo": null
        }
      ]
    }
  ]
}
~~~

Response에는 좌표·주소·카테고리·카카오 장소명·경로 원문을 포함하지 않는다. 완료 화면은 이 저장 응답만으로 표시할 수 있어야 한다.

---

## 8. 일정 조회·편집·삭제

### GET /api/travel-plans

현재 사용자의 일정 목록을 반환한다. 기본 정렬은 createdAt 내림차순이다. paging 방식은 인증 구현 전에 확정한다.

### GET /api/travel-plans/{travelPlanId}

저장된 완료 일정을 반환한다. 생성 Response와 같은 일정 구조를 사용한다.

- 카카오 장소·경로 API를 호출하지 않는다.
- 좌표나 지도를 반환하지 않는다.
- 저장된 사용자 작성 이름·시간표·외부 링크만 반환한다.
- 다른 사용자의 일정은 403, 없는 일정은 404다.

### PATCH /api/travel-plans/{travelPlanId}

완료 후 허용된 필드만 수정한다.

~~~json
{
  "title": "가족과 부산 여행",
  "placeEdits": [
    {
      "planPlaceId": 20,
      "displayName": "아침 바다 산책",
      "memo": "편한 신발 준비"
    }
  ]
}
~~~

- title, displayName, memo만 허용한다.
- 날짜·순서·시각·체류시간·이동수단·장소 ID·URL을 받지 않는다.
- 외부 API나 경로 계산을 실행하지 않는다.
- 일부 필드만 보내는 부분 수정이다.
- 모든 검증 후 하나의 트랜잭션으로 적용한다.

### DELETE /api/travel-plans/{travelPlanId}

자신의 일정 Aggregate 전체를 삭제한다. 외부 API를 호출하지 않으며 성공 시 204다.

날짜·순서·시각·체류시간·이동수단 또는 장소를 바꾸는 PUT API는 제공하지 않는다. 새 일정 생성으로 처리한다.

---

## 9. 음식점 선택

### POST /api/places/restaurants/search

estimate와 동일한 제작 데이터, 확정된 날짜·순서, 식사 날짜·종류와 메뉴 검색어를 브라우저 메모리에서 전달한다. 서버는 시간표와 식사 슬롯을 다시 계산해 존재 여부와 직전·직후 기준 장소를 검증한 뒤 후보를 조회한다. 기본 장소 검색과 동일한 저장·토큰 정책을 사용한다.

검색 반경은 1km, 3km, 최대 5km이며 후보가 없으면 빈 배열과 이유를 반환한다. 후보는 시간 적합성을 검사하고 Haversine 동선 이탈이 작은 순으로 정렬한다. 식당을 자동 확정하지 않는다.

관광지 검색의 districtFilterId를 음식점 검색에 자동 적용하지 않는다. 음식점은 식사 슬롯의 직전·직후 장소, 선택적 기준 관광지와 현재 지도 영역을 기준으로 탐색한다.

Response는 후보별 `selectionToken`, 임시 카카오 장소명·링크·좌표와 예상 이탈시간을 반환한다. 제작 화면은 직전·직후 장소, 기준 관광지와 후보 마커를 목록과 연동하고 지도 이동 후 현재 영역 재검색을 제공한다. 선택한 음식점만 최종 생성 Request의 식사 항목에 전달한다. 완료 후 저장 일정으로 음식점을 다시 검색하거나 추가·교체하는 기능은 제공하지 않는다.

---

## 10. 공유 API

### POST /api/travel-plans/{travelPlanId}/shares

소유자가 읽기 전용 공유 링크를 만든다. 토큰 원문 저장 여부, 해시와 만료 정책은 보안 ADR에서 확정한다.

### GET /api/shared/travel-plans/{shareToken}

인증 없이 저장된 완료 일정의 읽기 전용 Response를 반환한다.

- 카카오 API를 호출하지 않는다.
- 지도와 좌표를 반환하지 않는다.
- 수정·삭제 권한을 부여하지 않는다.
- 무효·만료 토큰은 404로 처리해 자원 존재 여부를 구분하지 않는다.

---

## 11. 제공자와 호출 제한

| 기능 | 사용자 분당 | 사용자 하루 |
|---|---:|---:|
| 장소 검색 | 20 | 300 |
| 자동차 경로 | 60 | 120 |
| 대중교통 경로 | 60 | 120 |
| AI 지역 추천 | 2 | 10 |
| AI 메뉴 분석 | 3 | 15 |

- 하루는 Asia/Seoul 자정에 초기화한다.
- 자동차와 대중교통 경로는 각각 사용자당 분당 60회·하루 120회이며 실제 제공자 요청과 재시도를 차감한다.
- 대중교통은 카카오맵 REST API를 사용하고 서비스 전체 일일 900건에서 신규 호출을 차단한다.
- 분할 경로 요청과 재시도는 각각 집계한다.
- 장소·자동차·대중교통 각 API의 공식 무료 한도를 따로 집계하고 90%에서 해당 API의 새 호출을 차단한다.
- 한도 숫자를 코드에 흩어 놓지 않고 설정과 정책 클래스로 관리한다.
- 제공자 가격·쿼터는 구현 시 공식 문서와 앱 콘솔에서 재확인한다.

---

## 12. 확인된 카카오 데이터 수명 계약

장소 선택 토큰, estimate, 일정 생성의 좌표 기반 계산 endpoint는 다음 확정 조건을 구현하고 검증한 뒤에만 완료 또는 운영 가능으로 표시한다.

1. 선택 좌표를 한 브라우저 탭의 JavaScript 메모리에 유지
2. 브라우저가 estimate와 create에 필요한 값을 각 요청으로 전달
3. 서버는 각 요청의 지역 변수에서 거리·순서·길찾기에만 사용하고 응답 전에 폐기
4. 완료·취소·새로고침·탭 종료 시 브라우저에서 폐기
5. 장소 ID·URL과 사용자 작성 정보만 영속 저장

2026-09-11 카카오 DevTalk 답변에 따라 위 일시 저장·참조 후 즉시 폐기 구조를 사용한다. 제공자 정책이 바뀌면 장소·좌표 원천과 이 문서의 5~7절 및 9절을 다시 검토한다.

---

## 13. 구현 순서

이 문서는 목표 계약이며 현재 endpoint 존재를 뜻하지 않는다. 구현 작업 ID와 완료 상태는 docs/11-command-roadmap.md와 docs/07-implementation-readiness.md를 따른다.
