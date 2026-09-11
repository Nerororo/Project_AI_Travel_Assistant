# Static Asset Instructions

## Responsibility

브라우저에 제공되는 HTML, CSS, JavaScript와 이미지 자산을 관리한다.

## Rules

- 서버 API 키, JWT secret, DB 정보, Authorization 헤더와 개인정보를 포함하지 않는다.
- 공개 키가 필요한 경우 서버 키와 분리하고 origin·도메인·API 제한을 적용한다.
- 카카오 좌표는 한 번의 제작 흐름에서만 메모리에 유지·전달하고 완료·취소·새로고침·탭 종료 시 즉시 폐기한다. 브라우저 저장소에는 기록하지 않는다.
- 같은 탭에서도 다른 인증 사용자의 장소 선택을 재사용하지 않고 사용자와 결합된 `selectionToken`만 현재 작성 요청에 전달한다.
- 숙소 탐색 지도는 제작 화면에서만 사용하고 완료·공유 화면에는 지도나 숙소 좌표를 남기지 않는다.
- 허용 뒤에도 좌표·주소·카테고리·제공자 장소명과 `selectionToken`을 localStorage·sessionStorage·IndexedDB에 저장하지 않는다.
- 거리, 경로 최적화, 추천 점수와 시간표를 JavaScript로 복제하지 않는다.
- 완료·공유 화면에서 외부 API, 지도, 마커와 경로선을 호출·표시하지 않는다.
- API 계약 변경 전 `docs/04-api-spec.md`를 확인한다.
- 실제 서비스 화면과 원본 템플릿 규칙은 각 하위 `AGENTS.md`를 따른다.
