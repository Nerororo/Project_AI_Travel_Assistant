# Routy Frontend Instructions

## 역할

이 폴더는 Routy의 작성·완료·공유 화면을 관리한다. 상위 하네스와 `INTEGRATION.md`를 함께 따른다. 다른 정적 화면과 `travela-1.0.0/**` 원본은 수정하지 않는다.

현재 파일은 정적 시안이며 실제 API·DB·인증·카카오 연동 완료로 간주하지 않는다. 화면 구현은 로드맵의 `W1` 단계에서 진행한다.

## 파일 책임

- `index.html`: 의미 구조와 정적 진입점
- `css/style.css`: 표현, 반응형과 모션
- `js/preview.js`: 미리보기 상태와 상호작용
- `js/preview.test.cjs`: 순수 UI 규칙 테스트
- `js/preview-server.cjs`: 로컬 미리보기
- `INTEGRATION.md`: 현재 시안과 목표 API 연결 지점

구조·표현·동작을 분리한다. 외부 라이브러리·CDN·dependency 추가 전 목적, 대안, 보안, 크기와 라이선스를 설명한다.

## 목표 화면 흐름

- 회원가입·로그인과 내 일정
- 서울·광역시·도 상위 항목에서 최종 시·군·구 하나 선택 또는 AI 지역 후보 3개 선택. 세종은 자체 선택
- `CAR` 또는 `PUBLIC_TRANSIT` 하나 선택
- 카카오 장소 검색과 사용자 표시 이름 직접 입력
- 체류 시간 10분 단위 조정
- Haversine 기반 추정 일정 확인
- 자연어 메뉴 제안 확인·수정과 식사 슬롯별 음식점 지도·목록 선택
- 실제 경로가 반영된 완료 요청
- 완료 일정 조회·제한 편집·삭제
- 읽기 전용 공유 일정

장소 유형은 노출하거나 변경하게 하지 않는다. 카카오 장소명은 사용자 표시 이름 입력란의 기본값·placeholder로 사용하지 않는다. 숙소는 기하 중앙값 5·10km, 중심 관광지 메도이드와 현재 지도 영역 검색을 제공하고 거리 점수 추천 없이 지도·목록에서 사용자가 선택하게 한다.

음식 화면은 AI가 제안한 메뉴 1~5개를 사용자가 수정·삭제·확정하게 한다. 식사 슬롯의 음식점 후보는 직전·직후 장소와 함께 지도·목록으로 표시하고 선택을 연동하며 현재 지도 영역 재검색을 제공한다. 완료 뒤 음식점 검색·추가·교체 UI는 제공하지 않는다.

점심은 11:30~14:00에서 12:00을, 저녁은 17:30~20:30에서 18:00을 우선해 표시한다. 음식점을 고르지 않은 식사도 장소 미정 상태로 유지하며 사용자가 선택하기 전 자동 확정하지 않는다.

## 서버와 화면 경계

- 프런트는 거리, 방문 순서, 시간표, 이동 시간, 추천 점수와 저장 가능 여부를 계산하지 않는다.
- UI validation은 빠른 안내용이며 서버 validation을 대체하지 않는다.
- API 성공 전 저장·수정·삭제 완료 상태로 바꾸지 않는다.
- 요청 중 중복 제출을 막고 오래된 검색 응답은 취소 또는 버전 비교로 무시한다.
- 오류 뒤에도 안전한 작성 입력과 기존 완료 일정 상태를 유지한다.
- 임시 payload, 가짜 ID와 무조건 성공 처리로 미구현 API를 우회하지 않는다.
- 정확한 endpoint와 DTO는 `docs/04-api-spec.md`를 따른다.

## 작성 데이터 수명

K0-02A는 2026-09-11 완료됐다. 실제 카카오 좌표를 유지·전달하는 기능은 다음 확정 조건을 지킨다.

- 좌표·주소·카테고리·제공자 장소명과 `selectionToken`은 현재 탭의 JavaScript 메모리에만 둔다.
- localStorage, sessionStorage와 IndexedDB에 저장하지 않는다.
- 새로고침, 완료, 취소와 탭 종료 시 작성 데이터를 폐기한다.
- 비밀번호, JWT, 사용자 원문, 외부 원문과 token을 console·오류 화면에 출력하지 않는다.
- 외부 입력은 안전한 DOM API로 표시하고 가능한 경우 `textContent`를 사용한다.

인증 토큰의 브라우저 보관 방식은 보안 ADR에서 확정하기 전 임의로 정하지 않는다.

## 추정·완료·공유

- 추정 결과는 `routeVerified=false`와 실제 경로 검증 전임을 표시한다.
- 시간 초과 시 장소를 자동 삭제하거나 체류 시간을 줄이지 않고 조정 방법을 안내한다.
- 기술 장애 fallback warning과 정상 경로 없음 오류를 구분한다.
- 경로 쿼터 부족 fallback은 일반 429 화면으로 바꾸지 않고 전체 일정이 거리 기반 예상시간이라는 warning을 보여준다.
- 완료 뒤에는 제목·사용자 장소 이름·메모만 편집 UI를 제공한다.
- 날짜·순서·시각·체류 시간·이동수단·장소 변경은 새 일정 흐름으로 안내한다.
- 완료·공유 화면은 저장된 제목·시간표·사용자 작성 정보·카카오 외부 링크만 표시한다.
- 완료·공유 화면에서는 외부 API, 지도, 마커와 경로선을 사용하지 않는다.

## 접근성·반응형

- 의미에 맞는 button, link, label, heading과 landmark를 사용한다.
- 입력과 오류 설명을 연결하고 동적 상태는 `aria-live` 또는 `role="alert"`로 알린다.
- 모달은 초점 이동·복원과 키보드 닫기를 지원한다.
- Tab, Shift+Tab, Enter, Space와 Escape로 핵심 흐름을 사용할 수 있어야 한다.
- 색만으로 선택·필수·오류를 구분하지 않고 초점 표시를 유지한다.
- `prefers-reduced-motion`과 모션 끄기를 유지한다.
- 모바일·데스크톱에서 핵심 입력, CTA, 시간표와 오류가 가려지지 않아야 한다.

## Change Envelope

수정 전 git 상태와 대상 diff를 확인하고 Task ID, Goal, Allowed·Conditional·Forbidden Paths, References Read, Verification을 설명한다.

- 화면 작업의 기본 Allowed는 `Routy/**`에서 사전 지정한 파일과 직접 대응 테스트다.
- Java, migration, application 설정과 build 파일은 Forbidden이며 별도 작업으로 다룬다.
- API 계약 오류는 화면 코드로 우회하지 않고 별도 문서·백엔드 작업으로 제안한다.
- 같은 폴더의 무관한 파일을 함께 포맷하거나 정리하지 않는다.

## 검증

변경에 적용되는 항목만 실행하고 마지막에 전체 테스트와 경계를 확인한다.

```powershell
node --check src/main/resources/static/Routy/js/preview.js
node --check src/main/resources/static/Routy/js/preview-server.cjs
node --test src/main/resources/static/Routy/js/preview.test.cjs
.\gradlew.bat -g .gradle-user test
git diff --check
```

자동 테스트는 실제 OpenAI·카카오 API를 호출하지 않는다. 화면 완료는 `docs/08-test-strategy.md`와 `docs/10-definition-of-done.md`의 브라우저·카카오 데이터 수명 계약 항목을 모두 확인한 뒤에만 표시한다.
