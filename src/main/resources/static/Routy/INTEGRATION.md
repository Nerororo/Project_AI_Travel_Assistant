# Routy 신규 화면 설계와 연결 가이드

## 1. 문서 목적

이 문서는 기존 정적 시안을 보존하거나 점진적으로 고치는 지침이 아니다. `W1-00`부터 Routy 화면을 새로 설계하고 이후 W1 작업에서 실제 API를 단계적으로 연결하기 위한 기준이다.

`W1-00`에서 `index.html`, `css/style.css`, `js/preview.js`를 신규 화면 골격으로 교체했다. `W1-01A`는 그 골격에 회원가입·로그인과 인증 수명을 연결했고, `W1-01B`는 지역 직접 검색과 AI 지역 추천을 연결했다. 나머지 제작·일정 기능은 각 후속 W1 작업에서 연결한다.

화면은 다음 원칙을 동시에 만족해야 한다.

- 여행을 시작하는 설렘과 경로를 정리하는 신뢰감을 함께 준다.
- 핵심 제작 흐름은 popup이나 modal 안에 가두지 않고 독립된 전체 페이지 workspace로 제공한다.
- HTML5 UP의 시각적 장점은 재해석하되 템플릿 코드·asset·jQuery 구조는 복사하지 않는다.
- 최신 HTML·CSS를 점진적 향상으로 사용하고 지원하지 않는 브라우저에서도 핵심 기능은 동작한다.
- 서버가 결정할 거리·순서·시간표·점수·저장 성공을 화면이 임의로 만들지 않는다.

## 2. 디자인 방향

### 2.1 가져올 장점

HTML5 UP의 특정 템플릿 하나를 기반으로 삼지 않고 다음 디자인 원리만 Routy 방식으로 결합한다.

- `Paradigm Shift`: 큰 타이포그래피, 비대칭 분할, 이미지와 문장이 이어지는 스토리텔링
- `Editorial`: 데스크톱 app shell, 명확한 탐색과 넓은 작업 영역
- `Phantom`·`Forty`: 여행지를 빠르게 훑을 수 있는 강한 카드와 이미지 타일
- 기존 Routy 시안: 선명한 파란색 정체성, 여행 카드의 깊이감, 단계별 상태와 접근성 기초

HTML5 UP 코드를 직접 가져오지 않으므로 해당 템플릿의 attribution, 구형 helper와 jQuery에 의존하지 않는다. 외부 코드나 asset을 도입해야 한다면 별도 Change Envelope에서 라이선스·대안·크기·보안을 먼저 설명한다.

### 2.2 Routy가 보여야 하는 인상

- 첫 화면은 여행 서비스다운 감성과 강한 브랜드 장면을 제공한다.
- 제작 화면은 장식보다 선택 상태, 지도, 시간과 오류 복구가 먼저 읽혀야 한다.
- 완료 화면은 지도 없는 여행 문서처럼 차분하고 읽기 쉬워야 한다.
- 3D와 모션은 브랜드 hero와 선택 피드백에만 제한하고 입력·오류·시간표의 이해를 방해하지 않는다.
- 지도 제공자 화면처럼 보이거나 예약·결제 서비스로 오해할 표현을 사용하지 않는다.

## 3. 페이지 정보 구조

핵심 기능을 하나의 거대한 landing page나 dialog에 넣지 않는다. 화면은 아래 page-level view로 나눈다.

| View | 역할 | 인증 |
|---|---|---|
| Landing | 서비스 소개, 작동 방식, 예시 일정, 시작 CTA | 공개 |
| Auth | 회원가입·로그인 | 공개 |
| Journey Workspace | 지역부터 완료 생성 직전까지 한 번의 제작 흐름 | 필요 |
| My Trips | 사용자의 완료 일정 목록 | 필요 |
| Trip Detail | 저장된 완료 일정 조회·제한 편집·삭제·공유 | 필요 |
| Shared Trip | 공유 토큰으로 읽는 고정 일정 | 공개 |

URL 구조는 실제 정적 제공 방식과 서버 fallback을 확인한 뒤 W1-00에서 확정한다. 경로를 확정하기 전 임시 URL을 API 계약처럼 문서화하지 않는다.

### 3.1 Modal 사용 제한

다음은 modal이나 dialog로 만들지 않는다.

- 로그인·회원가입의 주 화면
- 지역 직접 검색과 AI 지역 추천
- 이동수단·여행 기간 선택
- 관광지·숙소·음식점 검색과 지도 선택
- 일정 추정·날짜 조정·완료 생성
- 내 일정 목록과 일정 상세

`dialog`는 삭제 확인, 작성 취소 확인, 짧은 도움말처럼 현재 맥락을 잠시 보조하는 작업에만 사용한다. dialog를 닫아야 다음 제작 단계로 갈 수 있는 구조를 만들지 않는다.

## 4. 공통 app shell

### 4.1 데스크톱

- 상단에는 brand, 현재 작업 제목, 계정 영역을 둔다.
- landing은 비대칭 hero와 넓은 콘텐츠 흐름을 사용한다.
- workspace는 단계 rail, 주 작업 영역, 현재 선택 요약을 한 화면에 배치한다.
- 지도 단계는 목록과 지도를 나란히 보여주며 어느 한쪽도 popup 뒤에 숨기지 않는다.
- 내 여행과 상세 화면은 읽기 폭을 제한하되 시간표와 action은 충분한 공간을 갖는다.

### 4.2 모바일

- sidebar를 축소해 숨기는 방식이 아니라 compact header와 필요한 경우 bottom navigation으로 재구성한다.
- 주 CTA는 키보드와 브라우저 UI에 가리지 않는 sticky action 영역을 사용한다.
- `dvh`·`svh`와 `env(safe-area-inset-*)`를 용도에 맞게 사용한다.
- 목록과 지도는 사용자가 명시적으로 전환하되 선택 상태는 유지한다.
- hover를 전제로 정보를 숨기지 않고 최소 44px 이상의 조작 영역을 제공한다.
- 모바일 키보드가 열린 상태에서도 현재 label, 오류와 제출 action을 함께 확인할 수 있어야 한다.

### 4.3 공통 landmark

모든 page-level view는 의미에 맞는 `header`, `nav`, `main`, `aside`, `section`, `footer`를 사용한다. skip link, 한 페이지의 명확한 `h1`, 논리적인 heading 순서와 현재 위치 표시를 유지한다.

## 5. Journey Workspace

여행 제작은 별도 전체 페이지에서 이어지는 하나의 흐름이다. 단계가 바뀌어도 app shell, 현재 여행 제목, 진행 상태와 안전한 선택 요약은 유지한다.

### 5.1 단계 구조

1. 지역 선택
   - 직접 검색과 AI 추천을 같은 화면의 두 명확한 방법으로 제공한다.
   - AI는 정확히 3개 후보와 이유를 보여주고 사용자가 하나를 선택한다.
2. 여행 조건
   - 여행 기간 1~7일과 `CAR` 또는 `PUBLIC_TRANSIT` 하나를 선택한다.
3. 관광지 선택
   - 목록·지도·마커를 연동하고 특별시·광역시의 선택적 구·군 필터를 제공한다.
   - 사용자 표시 이름은 빈 입력에서 직접 작성하고 체류시간은 10분 단위로 조정한다.
4. 숙소 선택
   - 기하 중앙값 5·10km, 메도이드와 현재 지도 영역 탐색을 구분한다.
   - 거리 점수나 자동 추천 순위 없이 사용자가 지도에서 직접 선택한다.
5. 메뉴 분석
   - 자연어 요청과 AI가 구조화한 메뉴·검색어·이유·선택적 대상 관광지를 편집·삭제·확정한다.
   - AI 실패 뒤에도 직접 메뉴 입력으로 계속할 수 있다.
6. 추정 일정
   - `routeVerified=false`와 실제 경로 검증 전임을 명확히 표시한다.
   - 날짜별 장소 배정과 시간 부족 조정은 사용자가 수행하며 자동 삭제·체류 축소를 하지 않는다.
7. 음식점 선택
   - 식사 슬롯의 직전·직후 장소, 선택적 기준 관광지와 후보를 목록·지도에 함께 표시한다.
8. 검토와 완료 생성
   - 실제 경로 검증이 포함된 완료 요청임을 설명하고 중복 제출을 막는다.
   - 성공 Response를 받은 뒤에만 완료 일정 화면으로 전환한다.

### 5.2 단계 이동

- 진행 rail은 현재·완료·미진입 단계를 구분하고 색만으로 상태를 표현하지 않는다.
- 이미 완료한 안전한 단계로 돌아갈 수 있지만 상위 선택 변경의 폐기 범위를 먼저 알린다.
- 최종 여행 지역이 바뀌면 구·군 필터, 장소 결과·선택 token, 숙소, 메뉴 대상 연결, 추정 일정과 음식점 후보를 폐기한다.
- 브라우저 뒤로가기는 가능한 경우 page-level 이동과 일치시킨다. 작성 상태의 복원을 약속하지 않으며 새로고침 시 상태를 폐기한다.
- View Transition API를 사용할 수 있으나 미지원 환경에서는 즉시 전환되고 핵심 흐름이 동일해야 한다.

### 5.3 작업 화면 구성

- 왼쪽 또는 상단: 단계와 진행 상황
- 중앙: 현재 단계의 제목, 설명, 입력·검색 결과
- 오른쪽 또는 하단: 현재까지 확정한 지역·날짜·이동수단·장소 요약
- 지도 단계: 검색·필터·목록과 지도 사이에 동등한 선택 상태
- 하단 action: 이전, 현재 단계 저장이 아닌 다음 단계, 작성 취소

요약 영역은 서버가 확정하지 않은 내용을 일정 결과처럼 표현하지 않는다.

## 6. 시각 시스템과 최신 CSS

### 6.1 타이포그래피

- 한글 가독성을 우선하는 self-hosted variable font 후보를 검토한다.
- `clamp()`로 제목·본문·간격을 유동적으로 조절한다.
- 큰 제목에는 `text-wrap: balance`, 설명문에는 지원되는 경우 `text-wrap: pretty`를 사용한다.
- 작은 영문 대문자와 넓은 자간은 장식 label로만 제한하며 본문과 오류에는 사용하지 않는다.

### 6.2 색과 깊이

- 의미 기반 token을 정의하고 지원 환경에서는 `oklch()`와 `color-mix()`로 상태 색을 파생한다.
- 기본 hex fallback을 먼저 제공하고 `@supports` 안에서 향상한다.
- 흐림·반투명 효과는 텍스트 대비를 확인하고 `prefers-reduced-transparency` 환경에서 제거한다.
- 성공·경고·오류는 색과 함께 icon, 제목과 문장으로 구분한다.

### 6.3 Layout

- page shell은 CSS Grid의 명명 영역을 사용한다.
- 카드·폼·일정은 container query로 자신의 폭에 반응한다.
- 반복 카드에는 `auto-fit`·`minmax()`, 내부 행 정렬에는 필요한 경우 `subgrid`를 사용한다.
- 논리 속성으로 inline·block 방향을 표현하고 고정 픽셀 폭을 최소화한다.
- cascade layer를 token, reset, base, shell, component, utility, enhancement 순서로 관리한다.

### 6.4 모션과 3D

- hero의 여행 카드·경로 선·타이포에 제한된 깊이감을 제공한다.
- 정밀 pointer와 hover가 있는 환경에서만 pointer 기반 tilt를 사용한다.
- 모바일 자이로 센서와 권한이 필요한 device orientation 효과는 사용하지 않는다.
- 상태 전환에는 짧은 opacity·transform과 선택적 View Transition을 사용한다.
- scroll-driven animation은 landing 장식에만 사용하고 폼·지도·시간표에는 적용하지 않는다.
- `prefers-reduced-motion: reduce`에서는 모든 비필수 이동과 parallax를 제거한다.

### 6.5 현대 HTML

- 큰 작업 흐름은 semantic page section으로 만든다.
- 작은 보조 메뉴와 설명은 지원 환경에서 Popover API를 사용할 수 있다.
- 삭제·취소 확인은 native `dialog`를 사용하되 focus 이동·복원과 Escape 동작을 검증한다.
- native button, link, label, fieldset, legend와 form validation 관계를 우선하고 ARIA는 부족한 의미만 보완한다.

## 7. 컴포넌트와 상태 경계

다음 책임을 분리한다.

- API adapter: endpoint, header, requestId와 Response 변환
- session state: 현재 탭의 인증·작성 메모리 상태
- view model: API DTO를 화면 표시 상태로 변환
- renderer/component: DOM과 접근성 속성
- interaction: 제출, 취소, focus, 지도·목록 동기화

모든 주요 view는 다음 상태를 명시적으로 갖는다.

- initial
- loading
- success
- empty
- validation error
- recoverable provider error
- authentication expired
- rate limited
- duplicate request in progress 또는 already completed

고정 예시, 임시 ID와 무조건 성공 처리를 실제 adapter 안에 넣지 않는다. 미리보기 fixture가 필요하면 운영 adapter와 분리하고 화면에 예시임을 명확히 표시한다.

## 8. API 연결 순서

| W1 Task | 연결 범위 | 화면 결과 |
|---|---|---|
| W1-00 | API 연결 없음 | app shell, page-level view, workspace·상태 골격 |
| W1-01A | `POST /api/users`, `POST /api/auth/login` | 회원가입·로그인, 인증 실패·만료와 보호 화면 진입 |
| W1-01B | 지역 직접 검색·AI 지역 추천 | 지역 선택 workspace |
| W1-02 | 장소 검색·선택, P1-06 메모리 상태 | 관광지·숙소 목록과 지도 workspace |
| W1-02A | 메뉴 분석 | 메뉴 검토·직접 입력·확정 |
| W1-03 | 일정 추정 | 날짜별 추정 일정과 오류 복구 |
| W1-03A | 음식점 검색 | 식사 슬롯별 목록·지도 선택 |
| W1-04 | 완료 생성·조회 | 지도 없는 완료 일정 문서 |
| W1-05 | 목록·제한 편집·삭제·공유 | My Trips와 Trip Detail |
| W1-06 | 전체 | 반응형·키보드·브라우저·데이터 수명 회귀 |

정확한 endpoint와 DTO는 `docs/04-api-spec.md`를 따른다. 구현과 문서가 다르면 화면에서 payload를 조작해 우회하지 않고 별도 백엔드 또는 문서 Task로 제안한다.

### 8.1 W1-01A 연결 상태와 실행

- `js/auth.js`는 회원가입·로그인 JSON 요청, 오류의 안전한 표시 모델, 현재 탭 메모리 인증 상태와 만료를 담당한다. `js/preview.js`는 인증 폼·focus·보호 화면 이동과 작성 골격 초기화를 담당한다.
- 회원가입 201 뒤 로그인 화면으로 전환하며 자동 로그인하지 않는다. 로그인 200의 정상 응답 뒤에만 사용자가 요청했던 보호 화면으로 이동한다.
- Workspace·My Trips·Trip Detail은 비로그인 진입을 차단하고 Landing·Auth·Shared Trip은 공개한다. 서버의 실제 권한 검증은 기존 Spring Security가 담당한다.
- JWT는 closure 안에만 보관한다. 로그아웃·만료·pagehide에서 인증 및 현재 작성 골격 상태를 폐기하고 새로고침·BFCache 복귀에서도 보호 화면을 다시 검사한다. 후속 보호 API adapter는 `protectedRequest`의 Bearer 전달·401 처리·오래된 응답 무시 계약을 사용한다.
- 요청 중 중복 제출을 막으며 인증 탭 변경·화면 이탈 시 요청을 취소하고 늦은 응답을 무시한다. 비밀번호는 요청 결과 뒤 또는 이탈 시 입력에서 지우며 서버 응답 원문·JWT를 DOM·console에 표시하지 않는다.
- 실제 인증 연결은 Spring Boot가 제공하는 `/Routy/index.html`에서 같은 origin의 `/api/users`, `/api/auth/login`으로 동작한다. 서버·DB·JWT 환경 설정은 운영 문서를 따른다. `preview-server.cjs`의 4173 포트는 정적 미리보기만 제공하므로 실제 로그인 서버가 아니다.
- `auth.browser.test.cjs`는 임시 로컬 HTTP fake와 설치된 Chromium으로 인증 성공·실패, 보호 화면, 키보드, 모바일, 만료·새로고침을 검증한다. 실제 계정이나 외부 API를 사용하지 않는다. 실제 Spring 서버와 브라우저를 연결한 end-to-end smoke는 별도 실행으로 남는다.

### 8.2 W1-01B 연결 상태와 실행

- 지역 선택 단계는 같은 page-level workspace에서 직접 검색과 AI 추천을 전환한다. 직접 검색은 `GET /api/regions`의 `selectable`과 `placeSearchFilterable`을 그대로 사용해 최종 선택 지역과 다음 단계의 장소 검색 필터를 구분한다.
- AI 추천은 인증 Bearer와 요청마다 새 UUID `Idempotency-Key`를 사용해 `POST /api/ai/regions/recommend`를 호출한다. 중복 없는 정확히 3개의 유효한 후보일 때만 이름·상위 지역·이유와 선택 action을 표시한다.
- 선택한 지역 ID와 표시 정보는 현재 탭의 JavaScript 메모리에만 유지한다. 검색어와 AI 자연어, 응답, 인증 token은 브라우저 저장소나 console에 기록하지 않는다.
- 검색·추천 중에는 양쪽 제출을 잠그고 새 요청, 단계 이동, 로그아웃과 인증 만료 시 이전 요청을 취소하거나 늦은 응답을 무시한다. 오류 응답의 서버 message와 원문은 화면에 반영하지 않고 안정된 code별 복구 안내만 사용한다.
- `auth.browser.test.cjs`의 로컬 HTTP fake는 직접 검색 결과에서 선택 가능 지역과 필터 전용 구를 구분하고, 정확히 3개의 AI 후보 선택, 인증·멱등성 header, 메모리 선택 요약, 데스크톱·모바일 배치를 검증한다. 실제 OpenAI나 사용자 데이터는 사용하지 않는다.

### 8.3 W1-01C JWT TTL 정렬

- 로그인 성공은 `expiresInSeconds`가 특정 기본값과 같은지 비교하지 않고 유한한 양의 정수이며 브라우저에서 안전한 절대 만료 시각으로 계산 가능한지 검증한다. 문자열·0·음수·소수·안전 범위를 넘는 값은 malformed 성공 응답으로 거절한다.
- 만료 시각은 로그인 요청 시작 시각에 서버 TTL을 더해 계산하므로 응답 지연만큼 세션을 연장하지 않는다. 보호 요청과 화면 guard는 모두 같은 만료 시각을 사용한다.
- 브라우저 타이머의 단일 최대 지연보다 TTL이 길면 남은 시간을 여러 번 예약하며, 각 callback에서 현재 시각을 다시 확인해 조기 만료와 overflow를 막는다.

### 8.4 P1-06 장소 선택 메모리 상태

- `js/place-selection-state.js`는 W1-02가 사용할 관광지·숙소 후보와 선택 상태의 순수 메모리 경계다. 화면·DOM·지도·API 요청을 담당하지 않는다.
- `createStore()`로 작성 흐름마다 새 store를 만들고 `setContext(authenticatedUserId, regionId)`의 반환 세대를 검색 요청과 함께 보관한다. 사용자 또는 지역이 바뀌면 모든 후보와 선택을 폐기하며 이전 세대의 늦은 검색 응답과 선택 요청을 거절한다.
- `acceptSearchResults(generation, role, places)`는 `ATTRACTION`과 `HOTEL` 응답의 카카오 ID·URL, 제공자 표시 이름, 주소, 좌표, 기본 체류시간과 `selectionToken`을 방어적으로 복사·동결한다. 관광지는 여러 개, 숙소는 정확히 하나를 직접 선택할 수 있고 숙소 자동 점수·순위는 만들지 않는다.
- 완료·취소·인증 종료에서는 각각 `complete()`·`cancel()`·`authenticationEnded()`를 호출한다. `attachPageLifecycle(window)`는 `pagehide`에서 같은 전체 폐기를 수행하며 새로고침 복구를 제공하지 않는다.
- 모듈은 localStorage·sessionStorage·IndexedDB·cookie·서버 저장소를 사용하지 않는다. W1-02는 인증 Client의 session 종료 callback과 지역 변경 action을 이 모듈에 연결하고, 상태 객체나 token·좌표를 DOM diagnostic·console에 출력하지 않는다.
- `place-selection-state.test.cjs`는 방어적 복사, 관광지·숙소 선택, 사용자·지역 변경, 완료·취소·인증 종료, pagehide·새 store, 브라우저 저장소 미접근과 잘못된 provider 필드 거절을 실제 카카오 호출 없이 검증한다.

## 9. 작성 상태와 데이터 수명

- 검색 좌표·주소·카테고리·제공자 장소명과 `selectionToken`은 현재 탭 JavaScript 메모리에만 둔다.
- JWT도 승인된 인증 계약에 따라 메모리에서만 사용하고 로그아웃·인증 만료 시 제거한다.
- localStorage, sessionStorage와 IndexedDB에 작성 데이터나 token을 저장하지 않는다.
- 완료·취소·새로고침·탭 종료 시 작성 상태를 폐기한다.
- 인증 만료 시 작성 좌표와 token을 폐기한 뒤 로그인 화면으로 안전하게 이동한다.
- 비밀번호, JWT, 사용자 원문, 외부 원문, 좌표와 token을 console·DOM diagnostic·오류 문구에 출력하지 않는다.
- 카카오 장소명을 사용자 표시 이름의 기본값이나 placeholder로 사용하지 않는다.

## 10. 서버와 화면의 책임

프런트는 다음을 계산하거나 결정하지 않는다.

- Haversine 거리, 방문 순서, Nearest Neighbor와 2-opt
- 자동차·대중교통 이동 시간과 10분 단위 올림
- 체류 기본값 분류의 최종 판정
- 숙소 자동 추천 점수
- 음식점 시간 적합성과 동선 이탈 순위
- 날짜별 시간표와 저장 가능 여부
- 외부 장애 fallback 적용 여부와 저장 성공

UI validation은 빠른 안내일 뿐 서버 validation을 대체하지 않는다. 서버 성공 전에는 저장·수정·삭제가 완료된 것처럼 전환하지 않는다. 중복 제출을 막고 오래된 검색 응답은 취소하거나 request version으로 무시한다.

## 11. 완료·조회·공유 화면

- 완료 화면은 저장된 제목·시간표·사용자 장소 이름·메모·카카오 외부 링크만 사용한다.
- 모든 이동시간은 출처 구분 없이 예상 이동시간으로 표시한다.
- 완료·공유 화면에서는 카카오 API, 지도, 마커와 경로선을 사용하지 않는다.
- 완료 생성 Response의 warning은 현재 성공 화면에서만 보여주고 저장된 사실처럼 재구성하지 않는다.
- 완료 후에는 제목·사용자 장소 이름·메모만 편집한다.
- 날짜·순서·시각·체류시간·이동수단·장소 변경은 기존 일정을 보존하고 새 여행 제작으로 안내한다.
- 공유 화면은 인증 없이 읽기만 가능하고 소유자 action과 비공개 필드를 노출하지 않는다.

## 12. 접근성과 성능

- 키보드만으로 전체 핵심 흐름을 사용할 수 있어야 한다.
- focus-visible 표시를 유지하고 view 전환 뒤 heading 또는 첫 오류로 focus를 이동한다.
- 동적 상태는 용도에 맞게 `aria-live` 또는 `role="alert"`로 알리되 중복 안내하지 않는다.
- `prefers-contrast`, `forced-colors`, `prefers-reduced-motion`, `prefers-reduced-transparency`, `hover`와 `pointer` 환경을 고려한다.
- 긴 landing section은 검증 후 `content-visibility: auto`를 사용할 수 있으나 landmark와 접근성 트리를 함께 점검한다.
- animation은 transform·opacity 중심으로 제한하고 큰 blur·shadow와 지속 animation은 모바일에서 줄인다.
- 실제 이미지를 도입하면 크기 속성, `picture`, 적절한 format·`srcset`·`sizes`와 lazy loading을 적용한다.

## 13. W1-00 완료 기준

W1-00은 시각적으로 완성된 전체 제품이나 실제 API 연결 단계가 아니다. 다음 항목을 만족할 때 공통 골격이 준비된 것으로 본다.

- 기존 popup 중심 제작 흐름이 page-level Journey Workspace로 대체됐다.
- Landing, Auth, Workspace, My Trips, Trip Detail, Shared Trip의 landmark와 이동 관계가 드러난다.
- 데스크톱 app shell과 모바일 shell이 같은 정보 구조를 유지한다.
- workspace 단계 rail, 주 작업 영역, 선택 요약과 action 영역이 존재한다.
- 실제 API·가짜 성공·가짜 저장 없이 initial·loading·empty·error 상태 골격을 확인할 수 있다.
- 핵심 HTML이 semantic하고 keyboard focus 순서가 논리적이다.
- 모션을 끈 환경과 모바일 safe area에서 핵심 content와 CTA가 가려지지 않는다.
- 이후 W1 Task가 공통 token과 component를 재사용할 수 있다.

## 14. 검증

화면 변경 시 적용되는 파일에 대해 다음을 확인한다.

```powershell
node --check src/main/resources/static/Routy/js/preview.js
node --check src/main/resources/static/Routy/js/auth.js
node --check src/main/resources/static/Routy/js/preview-server.cjs
node --test src/main/resources/static/Routy/js/preview.test.cjs src/main/resources/static/Routy/js/auth.test.cjs
node --test src/main/resources/static/Routy/js/auth.browser.test.cjs
.\gradlew.bat -g .gradle-user test
git diff --check
```

파일 이름이 W1-00에서 변경되면 동일 책임의 새 파일로 검증 명령을 갱신한다. 자동 테스트는 실제 OpenAI·카카오 API를 호출하지 않는다. 모바일·데스크톱, 키보드, reduced motion, forced colors와 브라우저 저장소의 금지 데이터 부재를 별도로 확인한다.

브라우저 테스트는 Node.js 24와 로컬 Chrome을 사용한다. 다른 Chromium 설치 경로는 `CHROME_PATH`로 지정할 수 있다. 검증 캡처는 Git에서 제외된 `build/qa/`에 생성한다.
