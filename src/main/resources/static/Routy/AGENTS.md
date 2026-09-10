# Routy Frontend Instructions

## 역할과 범위

이 폴더는 Routy의 실제 서비스 화면이다. 이 규칙은 `src/main/resources/static/Routy/**`에 적용하며 루트, `resources`, `static`의 상위 `AGENTS.md`와 함께 따른다. 하위 규칙은 보안, 테스트, AI와 서버의 책임 분리, Change Envelope를 완화하지 않는다.

후속 UI 작업은 이 폴더에서 수행한다. `src/main/resources/static/**`의 다른 화면과 `src/main/resources/travela-1.0.0/**` 원본 템플릿은 수정하지 않는다.

현재 MVP 화면은 국가·도시 선택, AI 도시 후보, 여행 조건, 관광지, 호텔, 일정, 날짜 배정 수정, 음식점 검색, 인증·내 일정의 9단계 흐름을 지원한다. 결제, 예약, 커뮤니티, 소셜 로그인, 호텔 가격 비교, 실시간 채팅, 도로 polyline은 사용자가 별도 작업으로 요청하지 않는 한 UI에도 추가하지 않는다.

## 파일 책임

```text
Routy/
├── AGENTS.md
├── INTEGRATION.md         # 현재 미리보기 상태와 API 연결 지점
├── index.html             # 문서 구조와 정적 진입점
├── css/style.css          # 프로젝트 스타일, 반응형, 모션
├── img/                   # Routy 화면 자산
└── js/
    ├── preview.js         # 화면 상태, 렌더링, 상호작용
    ├── preview.test.cjs   # 순수 UI 규칙 테스트
    └── preview-server.cjs # 로컬 미리보기 서버
```

- HTML은 루트, CSS는 `css/`, 이미지·SVG는 `img/`, 브라우저 JavaScript와 직접 대응하는 테스트·미리보기 도구는 `js/`에 둔다.
- 구조는 HTML, 표현은 CSS, 동작은 JavaScript에 둔다. 인라인 이벤트 핸들러와 인라인 스타일을 새로 늘리지 않는다.
- 외부 라이브러리·CDN·npm dependency·vendor 파일을 추가하기 전에 목적, 대안, 보안, 크기, 라이선스 영향을 설명하고 승인을 받는다.
- 사용자가 요청하지 않으면 현재 시각 언어와 정보 계층을 전면 재설계하지 않는다.

## 책임 경계

- AI는 검증된 국가를 바탕으로 도시 후보 이름·국가 코드·추천 이유를 생성한다. Google Place ID, 거리, 일정, 방문 순서, 추천 순위, 저장 성공을 만들거나 추측하지 않는다.
- Google Places는 장소의 존재·유형·도시 소속과 Place ID를 제공한다.
- Google Routes는 정적 이동 시간·거리 행렬을 제공한다.
- Maps JavaScript API는 브라우저 자동완성과 지도 마커 표시만 담당한다.
- Spring Backend는 장소 검증, 저장, 일정 CRUD, 거리·경로·시간표 계산, 추천 점수, validation, 인증·인가를 담당한다.
- 프런트는 서버의 일정·경로·추천 알고리즘을 복제하지 않는다. UI validation은 빠른 피드백용이며 서버 validation을 대체하지 않는다.
- 자유 입력 문자열, 배열 순번, 예시 키를 Google Place ID로 취급하지 않는다.

## 화면 상태와 API 연결

현재 `js/preview.js`의 고정 데이터와 `simulate()`는 디자인 미리보기다. 실제 API 결과로 표현하거나 운영 저장소처럼 사용하지 않는다. 구체적인 현재 상태와 연결 지점은 `INTEGRATION.md`를 따른다.

- API 호출, JSON 변환, timeout·취소, 오류 변환을 렌더링 코드와 분리한다. 새 모듈은 수정 전에 파일과 책임을 Change Envelope에 명시한다.
- 서버 응답으로 확정된 상태와 사용자가 편집 중인 임시 상태를 분리한다.
- POST·PUT·DELETE는 성공 응답 후에만 확정 상태를 변경한다.
- 수정·삭제 실패 시 기존 일정과 목록을 유지하고 오류를 별도 표시한다.
- GET 응답을 표시할 때 저장된 방문 순서·시각·식사 슬롯을 프런트에서 재계산하지 않는다.
- 음식점 후보와 지도 오류 상태는 일정 상태와 분리한다. 검색이나 지도 실패가 일정을 변경하거나 지우면 안 된다.
- 요청 중 중복 제출을 막는다. 검색처럼 응답 순서가 바뀔 수 있는 요청은 취소 또는 버전 비교로 오래된 응답을 무시한다.
- 뒤로 이동하거나 오류 후 재시도할 때 이미 입력한 값과 선택을 보존한다.
- 로딩, 빈 결과, validation, not found, 인증·권한, provider 장애를 구분해 표시한다.
- API가 없거나 문서와 구현이 다르면 임시 payload, 가짜 ID, 무조건 성공 처리로 우회하지 않고 별도 백엔드·계약 작업으로 제안한다.

정확한 endpoint, Request/Response 필드, 상태 코드, validation 수치, 오류 코드는 `docs/04-api-spec.md`와 해당 요구사항을 단일 기준으로 사용한다. 하네스에 해당 계약을 복제하지 않는다.

## 보안과 개인정보

- OpenAI 키, Google 서버 키, DB 정보, access token, Authorization 헤더를 정적 파일, 문서, fixture, 로그에 넣지 않는다.
- 브라우저 지도 키는 서버 키와 분리하고 허용 origin과 필요한 API로 제한한다. Google attribution 정책을 지킨다.
- 인증 계약 확정 전 토큰을 임의로 브라우저 저장소에 보관하지 않는다. 인증 전 브라우저 저장소를 영구 일정 저장소로 사용하지 않는다.
- 비밀번호, 사용자 자연어 원문, provider 원문 요청·응답을 console이나 오류 화면에 기록하지 않는다.
- 외부 입력은 escape한다. 가능하면 `innerHTML`보다 `textContent`와 DOM API를 사용한다.
- 운영 화면에 미리보기 상태 선택기, stack trace, request payload, 민감한 내부 진단 정보를 노출하지 않는다.

## 접근성과 반응형

- 의미에 맞는 `button`, `a`, `label`, heading, landmark를 사용하고 클릭용 `div`를 만들지 않는다.
- 입력에는 label과 오류 설명을 연결하고 동적 상태는 적절한 `aria-live` 또는 `role="alert"`로 알린다.
- 모달은 초점 이동·복원과 키보드 닫기를 지원한다.
- 핵심 흐름은 Tab, Shift+Tab, Enter, Space, Escape로 사용할 수 있어야 한다.
- 색만으로 선택·필수·오류를 구분하지 않고 초점 표시를 제거하지 않는다.
- `prefers-reduced-motion`과 화면의 모션 끄기 기능을 유지한다.
- 모바일과 데스크톱에서 핵심 입력, CTA, 시간표, 오류가 가려지지 않는지 확인한다.
- 지도 없이도 시간표를 이해할 수 있어야 하며 지도 실패가 시간표를 가리지 않아야 한다.

## 선택적 참조 규칙

모든 코드와 문서를 한꺼번에 읽지 않는다. 먼저 이 파일과 변경 대상 파일·직접 참조 파일만 읽고, 다음 조건에 해당할 때 관련 문서의 해당 절만 추가로 확인한다.

| 변경 내용 | 참조 기준 |
|---|---|
| 현재 미리보기와 연결 위치 | `INTEGRATION.md` |
| MVP 흐름·입력·시간·추천 규칙 | `docs/01-requirements.md` 관련 FR |
| 프런트와 도메인 책임 변경 | `docs/02-architecture.md` 관련 절 |
| Place ID·저장·소유권 가정 | `docs/03-database.md` 관련 절 |
| endpoint·DTO·상태·오류 | `docs/04-api-spec.md` 해당 endpoint |
| 설계 선택 변경 | `docs/06-decisions.md` 관련 ADR |
| 구현 여부와 미확정 사항 | `docs/07-implementation-readiness.md` |
| UI 테스트 범위 | `docs/08-test-strategy.md`의 실제 서비스 화면 |
| 키·외부 API·배포 | `docs/09-operations.md` 관련 절 |
| 완료 판정 | `docs/10-definition-of-done.md`의 공통·사이트 항목 |
| S1 순서와 선행 조건 | `docs/11-command-roadmap.md`의 해당 S1 작업 |
| 쓰기 경계 | `docs/12-harness-boundaries.md`의 UI 단계 |

문서와 코드가 다르면 추측으로 맞추지 않는다. 차이를 읽기 전용으로 확인하고 화면 변경과 계약 변경을 분리한다.

## Change Envelope

수정 전 `git status --short`와 대상 파일의 기존 diff를 확인하고 사용자에게 다음을 설명한다.

```text
Task ID: S1 ID 또는 사용자 지정 작업명
Goal: 이번 작업에서 완료할 한 가지 결과
Allowed Paths: Routy 안에서 실제 수정할 파일
Conditional Paths: 직접 대응 테스트·계약 문서와 변경 조건
Forbidden Paths: 다른 정적 화면, 원본 템플릿, 백엔드·migration·설정
References Read: 이 AGENTS.md와 직접 관련된 기준
Verification: 문법·단위·브라우저·전체 테스트와 경계 검사
```

- 같은 폴더라는 이유로 전체 파일을 읽거나 포맷하지 않는다.
- UI 테스트와 `docs/**`는 파일 단위로 이유를 설명하고 승인받은 경우에만 수정한다.
- Java, migration, application 설정, build 파일은 UI 작업에서 수정하지 않고 별도 작업으로 분리한다.
- dependency, 외부 서비스, API, schema, 인증 방식 변경은 구현 전에 대안과 영향을 설명하고 별도 승인을 받는다.

## 검증과 완료

현재 기본 검증은 다음과 같다.

```powershell
node --check src/main/resources/static/Routy/js/preview.js
node --check src/main/resources/static/Routy/js/preview-server.cjs
node --test src/main/resources/static/Routy/js/preview.test.cjs
.\gradlew.bat -g .gradle-user test
git diff --check
```

- HTML/CSS 변경은 모바일·데스크톱과 키보드 흐름을 확인한다.
- 상태·API 변경은 성공과 최소 한 개의 실패·경계 시나리오, 중복 제출, 오래된 응답, 실패 시 상태 보존을 테스트한다.
- 자동 테스트는 실제 OpenAI·Google API를 호출하지 않는다. 실제 지도·provider는 제한된 키를 사용하는 별도 수동 smoke로 확인한다.
- 작업 후 변경 파일을 Change Envelope와 대조하고 기존 사용자 변경 보존과 범위 밖 파일 유무를 확인한다.
- 검증하지 못한 항목은 이유와 범위를 남기며, `docs/10-definition-of-done.md`를 충족하기 전에는 사이트 완료로 표시하지 않는다.
- 사용자가 요청하지 않으면 `git add`, commit, stash, reset, checkout을 실행하지 않는다.
