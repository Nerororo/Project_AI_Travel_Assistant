# Routy 정적 화면 상태와 연결 안내

## 1. 현재 상태

이 폴더는 Routy 서비스 화면으로 발전시킬 정적 시안이다. HTML·CSS·JavaScript와 로컬 미리보기 도구로 구성되며 현재 백엔드 API, DB, 인증과 실제 카카오·OpenAI에 연결돼 있지 않다.

표시되는 장소와 일정은 디자인 확인용 고정 예시다. 실제 검증·추천·경로 계산·저장 성공으로 해석하지 않는다.

## 2. 미리보기

프로젝트 루트에서 실행한다.

```powershell
node src/main/resources/static/Routy/js/preview-server.cjs
```

주소는 `http://127.0.0.1:4173`이다. Spring Boot에서 정적 자산을 제공할 때의 경로는 `/Routy/index.html`이다.

```powershell
node --check src/main/resources/static/Routy/js/preview.js
node --check src/main/resources/static/Routy/js/preview-server.cjs
node --test src/main/resources/static/Routy/js/preview.test.cjs
```

## 3. 새 제품 흐름과 화면 연결

현재 시안의 기존 단계 흐름은 목표 계약이 아니다. 실제 W1 작업에서는 아래 흐름에 맞춰 화면 상태를 재구성한다.

| 흐름 | API·데이터 | 화면 책임 |
|---|---|---|
| 회원가입·로그인 | `POST /api/users`, `POST /api/auth/login` | 인증 오류와 만료 처리 |
| 지역 직접 검색 | `GET /api/regions?query=` | 서울·광역시·도에서 하위 최종 시·군·구 하나 선택, 세종은 자체 선택 |
| AI 지역 추천 | `POST /api/ai/regions/recommend` | 정확히 3개 후보와 이유 표시 |
| 이동수단 선택 | 작성 상태의 `CAR` 또는 `PUBLIC_TRANSIT` | 일정 하나에 하나만 유지 |
| 장소 검색 | `POST /api/places/search` | 카카오 검색 결과는 작성 중에만 표시 |
| 장소 확정 | `selectionToken`, 빈 사용자 이름, 체류 시간 | 유형 미노출, 이름 1~50자, 10분 조정 |
| 숙소 탐색 | 기하 중앙값 5·10km, 메도이드, 지도 영역 | 관광지·숙소 마커와 목록 연동, 점수 순위 없이 직접 선택 |
| 메뉴 분석 | `POST /api/ai/menus/analyze` | 자연어에서 메뉴 1~5개·검색어·이유·대상 관광지 제안 후 사용자 확정 |
| 일정 추정 | `POST /api/travel-plans/estimate` | `routeVerified=false`를 명확히 표시 |
| 음식점 탐색 | `POST /api/places/restaurants/search` | 식사 슬롯별 후보·직전·직후 장소를 지도와 목록에 표시하고 직접 선택 |
| 완료 생성 | `POST /api/travel-plans` | 실제 경로·시간 초과 결과 후에만 저장 성공 |
| 내 일정·편집 | 목록·상세·PATCH·DELETE | 제목·사용자 이름·메모만 편집 |
| 공유 | share 생성과 공개 GET | 읽기 전용 고정 일정 표시 |

정확한 endpoint와 DTO는 `docs/04-api-spec.md`를 따른다. 구현과 문서가 다르면 임시 payload나 가짜 성공으로 우회하지 않는다.

## 4. 작성 상태와 데이터 수명

2026-09-11 카카오 DevTalk 답변으로 일시 사용·즉시 폐기 구조가 허용됨을 확인했다. 다음 규칙으로 구현한다.

- 검색 응답의 좌표·주소·카테고리·제공자 장소명은 현재 탭의 JavaScript 메모리에만 둔다.
- localStorage, sessionStorage와 IndexedDB에 저장하지 않는다.
- 새로고침, 완료, 취소와 탭 종료 때 작성 데이터를 폐기한다.
- `selectionToken`도 영속 브라우저 저장소에 넣지 않는다.
- 카카오 장소명을 사용자 표시 이름의 기본값이나 placeholder로 넣지 않는다.
- 비밀번호, JWT, 사용자 원문, 외부 원문과 token을 console에 출력하지 않는다.

카카오 임시 사용 조건 확인만으로 실제 좌표를 유지·전달하는 화면 코드를 완료로 표시하지 않는다. 메모리 한정 사용과 모든 종료 경로의 즉시 폐기를 구현·검증해야 한다.

## 5. 서버와 화면의 책임

프런트는 다음을 계산하지 않는다.

- Haversine 거리와 방문 순서
- Nearest Neighbor와 2-opt
- 자동차·대중교통 이동 시간
- 체류 기본값 분류의 최종 판정
- 숙소 자동 추천 점수
- 음식점 시간 적합성과 Haversine 동선 이탈 순위
- 날짜별 시간표와 저장 가능 여부

UI validation은 빠른 안내용이며 서버 validation을 대체하지 않는다. 서버 성공 응답 전에는 저장·수정·삭제가 완료된 것처럼 상태를 바꾸지 않는다. 중복 제출을 막고 검색 요청의 응답 순서가 바뀌면 오래된 응답을 무시한다.

## 6. 추정·완료·조회 화면

- 추정 화면은 실제 경로 검증 전임을 표시한다.
- 완료 요청이 시간 초과로 실패하면 장소를 자동 제거하거나 체류 시간을 줄이지 않고 사용자가 조정할 수 있게 한다.
- 기술적 경로 장애 fallback의 warning과 정상 경로 없음 오류를 구분한다.
- 완료 화면은 저장된 제목·시간표·사용자 장소 이름·메모·카카오 외부 링크만 사용한다.
- 완료·공유 화면에서는 카카오 API를 호출하거나 지도·마커·경로선을 표시하지 않는다.
- 숙소와 음식점 지도는 작성 중에만 제공하며 목록·마커 선택과 현재 지도 영역 재검색을 연동한다.
- 완료 후 날짜·순서·시각·체류 시간·이동수단·장소는 편집 UI를 제공하지 않는다.

## 7. 구현 경계

실제 화면 작업은 로드맵의 `W1` 단계와 이 폴더의 `AGENTS.md`를 따른다.

- API adapter와 렌더링·상태 코드를 분리한다.
- 고정 예시와 미리보기 제어는 운영 흐름에서 제거한다.
- 백엔드 계약 변경이 필요하면 화면 작업에서 수정하지 않고 별도 작업으로 제안한다.
- 원본 `travela-1.0.0/**`은 수정하지 않는다.
- 외부 라이브러리·CDN·dependency 추가 전 목적과 대안을 설명한다.

## 8. 남은 검증

현재 확인된 것은 정적 시안뿐이다. 다음은 아직 완료되지 않았다.

- 새 제품 흐름에 맞춘 UI 재설계
- 실제 API·인증·DB 연결
- AI 메뉴·식사 이동 여유·숙소와 음식점 지도 흐름 구현
- 브라우저 저장소의 좌표 잔존 검사
- 성공·시간 초과·경로 없음·한도 초과·인증 만료 흐름
- 모바일·데스크톱·키보드 접근성
- 제한된 키를 사용한 별도 smoke
- `docs/10-definition-of-done.md`의 브라우저·카카오 데이터 수명 계약 항목
