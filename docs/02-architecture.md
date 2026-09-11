# 시스템 아키텍처

## 1. 목적과 원칙

Routy는 국내 여행 장소를 선택하고 체류 시간과 이동 시간을 조합해 실행 가능한 일정을 만드는 Spring Boot 백엔드다. 이 문서는 목표 구조를 설명하며 구현 상태는 `docs/07-implementation-readiness.md`에서 관리한다.

- AI는 지역 후보와 음식 검색어 같은 자연어 결과만 만든다.
- 일정 배치, 거리·시간 계산, 경로 최적화와 시간 검증은 서버가 결정한다.
- 외부 HTTP 구현은 Client 인터페이스 뒤에 격리한다.
- 작성 중 데이터와 완료된 일정을 분리한다.
- 외부 호출과 계산을 마친 뒤 완료 일정 Aggregate를 한 번에 저장한다.
- 완료·공유 일정 조회에는 외부 API를 호출하지 않는다.

## 2. 전체 구성

```text
Browser (작성 중 임시 정보 / 완료 일정 표시)
  → Spring Boot API
      ├─ Controller / DTO / Validation
      ├─ Authentication / Authorization / Rate Limit
      ├─ User / Region / Place / Route / TravelPlan / Recommendation / AI
      ├─ 순수 정책과 알고리즘
      ├─ 외부 Client 인터페이스
      └─ Repository / JPA / MySQL

Static region data: src/main/resources/data/regions.json
External: Kakao Local, Kakao Mobility, Kakao Map 대중교통, OpenAI
```

2026-09-11 카카오 DevTalk 답변으로 장소 좌표를 제작 중 일시적으로 사용하고 즉시 폐기하는 구조가 허용됨을 확인했다. 이 확인은 구현 완료를 뜻하지 않으며 실제 구현은 아래 데이터 수명 규칙을 지켜야 한다.

## 3. Spring 계층

```text
HTTP Request → Controller → Service
             → Repository 또는 Client 인터페이스
             → Response DTO
```

Controller는 URL, Request DTO, 형식 검증, 인증 사용자 전달과 HTTP 응답을 담당한다. Service는 비즈니스 규칙과 작업 순서를 담당한다. Repository는 같은 도메인의 Entity 조회·저장만 수행한다. Request·Response DTO와 Entity를 분리하며 Entity를 직접 반환하지 않는다.

Service는 다른 도메인의 공개 Service·전달 DTO만 사용하고 외부 HTTP 구현을 직접 참조하지 않는다. 외부 호출 중 DB 트랜잭션을 열어 두지 않는다.

## 4. 목표 패키지

```text
com.example.travel
├─ user/{controller,service,repository,domain,dto}
├─ region/{controller,service,domain,dto}
├─ place/{controller,service,client,dto}
├─ route/{service,client,algorithm,dto}
├─ travelplan/{controller,service,repository,domain,dto}
├─ recommendation/{service,dto}
├─ ai/{service,client,dto}
└─ global/{config,exception,security,common}
```

패키지는 구현 시 필요한 만큼만 만든다. `global`에는 공통 보안·예외·설정·도구만 두고 여행 정책은 해당 도메인에 둔다.

## 5. 도메인 책임

### user

회원가입, 비밀번호 해시, 로그인, JWT, 인증 사용자 식별과 일정 소유권을 담당한다. 일정은 처음부터 인증 사용자 소유로 저장한다.

### region

`regions.json`을 시작 시 검증·적재하고 서울특별시·광역시·도를 상위 탐색 항목으로, 그 아래 시·군·구를 최종 선택 항목으로 제공한다. 읍·면·동은 제외하며 세종특별자치시처럼 하위 시·군·구가 없는 예외는 자체 선택을 허용한다. 지역 ID, 이름, 상위 지역, 선택 가능 여부와 대표 좌표를 제공하며 AI가 선택할 허용 목록도 소유한다. 완료 일정에는 최종 지역 하나의 `regionId`와 당시 표시 이름을 저장한다.

### ai

여행 조건에서 정확히 3개의 허용 지역 ID와 이유를 만들고, 음식 자연어에서 최대 5개의 메뉴명·카카오 검색어·이유·대상 관광지를 구조화한다. AI는 식당 선택, 장소 검증, 거리, 방문 순서, 시간표와 추천 점수를 결정하지 않는다.

```text
AiService → AiClient → OpenAiClient / FakeAiClient
```

### place

카카오 장소 검색, 반경·주소 소속 검증, 지도 영역 숙소·음식점 탐색, 응답 변환, 카테고리별 기본 체류 시간과 짧은 수명의 `selectionToken`을 담당한다.

장소 유형·카테고리는 노출하거나 저장하지 않는다. 카카오 장소명은 검색 결과에만 표시하고 일정 표시 이름 입력란은 비워 둔다. 완료 일정에는 카카오 장소 ID·URL과 사용자가 작성한 이름·메모·체류 시간만 저장한다.

```text
PlaceService → KakaoPlaceClient → KakaoLocalHttpClient / FakeKakaoPlaceClient
```

### route

Haversine 거리, Nearest Neighbor 초안, 2-opt 개선, 이동 시간 추정과 최종 인접 구간의 외부 경로 검증을 담당한다. 제공자가 반환한 예상 이동시간은 10분 단위로 올린다.

```text
RouteService
  ├─ route/algorithm (순수 Java)
  └─ RouteClient
       ├─ KakaoMobilityRouteClient (CAR)
       ├─ KakaoTransitRouteClient (PUBLIC_TRANSIT)
       └─ FakeRouteClient
```

`route/algorithm`은 Spring, JPA, HTTP와 AI에 의존하지 않는다. 일정 하나는 `CAR` 또는 `PUBLIC_TRANSIT` 하나만 사용한다.

### travelplan

날짜·활동 시간·장소·체류 시간 검증, 날짜별 배치, 이동 시간 조합, 종료 시각 검사, Aggregate 저장, 소유자 조회·삭제, 제한된 텍스트 수정과 공유 DTO를 담당한다. 다른 도메인의 Repository나 HTTP 구현을 직접 참조하지 않는다.

### recommendation

제작 중 음식점 후보의 시간 적합성과 Haversine 동선 이탈 점수·정렬을 서버 규칙으로 계산한다. 숙소에는 추천 점수를 부여하지 않고 place가 기하 중앙값·메도이드·지도 영역을 중심으로 실제 후보를 제공하며 사용자가 지도에서 선택한다. 음식 자연어 해석은 AI가 담당하지만 음식점 후보 조회와 점수 계산은 서버 책임이다.

## 6. 작성 데이터 수명

```text
카카오 검색 응답
  → 브라우저 JavaScript 메모리
  → estimate 또는 create 요청
  → 서버가 해당 요청의 지역 변수에서만 사용
  → 응답 전 서버 참조 폐기
  → 완료·취소·새로고침·탭 종료 때 브라우저 상태 폐기
```

좌표, 주소, 카테고리와 제공자 장소명은 영속 저장하지 않는다. 브라우저의 localStorage, sessionStorage, IndexedDB에도 저장하지 않는다. MySQL 운영 테이블에는 호출량 숫자와 `requestId` 처리 상태만 두며 payload·response를 저장하지 않는다. `selectionToken`은 선택값 무결성을 위한 서명 토큰이며 서버 장소 캐시가 아니다.

이 흐름은 2026-09-11 카카오 DevTalk 답변으로 허용 범위를 확인했다. 브라우저는 한 번의 작성 흐름 동안 메모리에서만 값을 유지할 수 있고, 서버는 estimate와 create 각 요청에서 전달받은 좌표를 사용한 뒤 응답 전에 폐기한다. 서버 저장소를 통해 요청 사이에 좌표를 넘기거나 영속 저장·캐시·로그에 남기지 않는다.

## 7. 2단계 일정 계산

### 추정

`POST /api/travel-plans/estimate`는 DB와 실제 경로 API를 사용하지 않는다.

```text
입력·토큰 검증 → Haversine 행렬
→ Nearest Neighbor → 2-opt
→ 추정 이동 시간 → 체류·식사 조합
→ routeVerified=false
```

### 완료

`POST /api/travel-plans`는 클라이언트의 시간 계산을 신뢰하지 않는다. 모든 장소의 선택 토큰, 날짜와 순서를 다시 검증하고 사용자가 확정한 배치는 보존한 채 이동시간과 시간표를 재계산한다.

```text
인증·한도·중복 검사 → 입력·토큰 검증
→ 사용자 확정 날짜·순서 검증
→ 최종 인접 구간만 실제 경로 조회
   CAR: Kakao Mobility
   PUBLIC_TRANSIT: Kakao Map
→ 10분 단위 올림 → 종료 시각 검증
→ 저장 금지 필드 제거 → 짧은 트랜잭션으로 저장
```

시간을 넘으면 저장하지 않으며 서버가 장소를 삭제하거나 체류 시간을 줄이지 않는다. 정상적인 경로 없음은 422다. 일시적 기술 장애는 한 번 재시도하고 재실패 때만 Haversine 추정값과 warning으로 대체할 수 있다.

## 8. 완료 일정과 조회

```text
User
  └─ TravelPlan
      ├─ TravelPlanDay
      │   └─ TravelPlanItem
      └─ PlanPlace
```

`TravelPlan`이 Aggregate Root다. 일정, 날짜, 항목과 장소 snapshot을 하나의 생성 트랜잭션으로 저장한다.

완료 후에는 제목, 사용자 장소 표시 이름과 메모만 수정할 수 있다. 날짜, 순서, 시각, 체류 시간, 이동수단, 장소 ID·URL을 바꾸려면 새 일정을 만든다.

완료·공유 조회는 저장 데이터만 사용한다. 카카오·OpenAI를 호출하지 않고 좌표, 주소, 카테고리와 경로선을 반환하지 않는다. 고정 HTML 화면에는 사용자 작성 이름, 시간표와 카카오 외부 링크만 표시한다.

## 9. 외부 API와 장애 경계

| 기능 | 위치 | 서버 책임 |
|---|---|---|
| OpenAI 지역·메뉴 분석 | `ai/client` | 허용 목록과 구조 검증 |
| 카카오 장소 검색 | `place/client` | 검색 정책, 시간 변환, 금지 필드 폐기 |
| 자동차 경로 | `route/client` | 인접 구간, 재시도, 10분 올림 |
| 대중교통 경로 | `route/client` | 인접 구간, 재시도, 10분 올림 |

실제 Client만 인증 헤더, timeout과 응답 매핑을 담당하며 테스트는 Fake Client를 사용한다. 한도는 사용자별 분·일 단위이고 일일 기준은 `Asia/Seoul`이다. 외부 API 원문, 좌표, API 키와 개인정보는 로그나 오류 응답에 남기지 않는다.

## 10. 트랜잭션과 테스트

- 외부 통신과 순수 계산은 저장 트랜잭션 밖에서 수행한다.
- 모든 검증 뒤 Aggregate 전체를 한 트랜잭션으로 저장한다.
- 부분 저장을 허용하지 않는다.
- 적용된 versioned migration은 고치지 않고 새 버전을 추가한다.
- 순수 알고리즘과 시간 정책은 Spring 없이 단위 테스트한다.
- Service는 Fake Client로 성공, 경로 없음, 재시도, fallback과 시간 초과를 검증한다.
- Repository는 Aggregate·DB 제약, Controller는 인증·validation·상태 코드를 검증한다.
- 실제 외부 API는 기본 자동 테스트에서 호출하지 않는다.

세부 기준은 `docs/08-test-strategy.md`와 `docs/10-definition-of-done.md`를 따른다.

## 11. 확인된 카카오 데이터 수명 계약

다음은 2026-09-11 카카오 DevTalk 답변을 반영한 Accepted 상태의 필수 조건이다.

1. 선택 좌표를 한 번의 작성 흐름 동안 브라우저 JavaScript 메모리에만 둔다.
2. estimate와 create에 필요한 값을 브라우저가 각 요청으로 전달한다.
3. 서버는 각 요청의 지역 변수에서 거리·순서·경로 계산에만 사용하고 응답 전에 폐기한다.
4. 완료·취소·새로고침·탭 종료 때 브라우저 상태를 폐기한다.
5. 완료 일정에는 카카오 장소 ID·URL과 사용자 작성 정보만 저장한다.

정적 지역 데이터, 인증, 순수 알고리즘, Client 인터페이스와 Fake, 저장 모델은 좌표의 수명과 저장 금지 계약을 침범하지 않도록 분리한다.
