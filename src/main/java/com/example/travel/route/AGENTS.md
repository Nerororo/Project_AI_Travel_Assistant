# Route Domain Instructions

## Responsibility

거리, 추정 이동 시간, 방문 순서와 실제 자동차·대중교통 구간 검증을 관리한다.

## Rules

- `route/algorithm`은 Haversine·기하 중앙값·메도이드·Nearest Neighbor·2-opt를 순수 Java로 구현하고 Spring·JPA·HTTP·AI에 의존하지 않는다.
- 동률, 시작점, 종료 조건과 반복 상한을 결정적으로 유지한다.
- 실제 통신은 `route/client`에 두고 CAR와 PUBLIC_TRANSIT Client를 분리한다.
- 일정 하나에서 이동수단을 혼합하지 않는다.
- 최종 후보의 인접 구간만 실제 경로로 조회한다.
- 경로 API가 반환한 예상 초를 `ceil(seconds/600)*10`분으로 올리고 고정 buffer를 더하지 않는다.
- 정상 경로 없음은 fallback하지 않는다. 기술 장애만 한 번 재시도한 뒤 Haversine 추정값을 사용할 수 있다.
- 경로 원본 초·분, 원문, 좌표와 polyline은 저장 계약으로 전달하지 않고 구간별 10분 단위 예상시간 숫자만 반환한다.
- 외부 호출·재시도·fallback과 한도는 운영 계약을 따른다.
- 자동차·대중교통은 각각 사용자당 60회/분·120회/일로 제한하고 대중교통은 서비스 전체 일일 900건에서 신규 호출을 차단한다.
- 완료 생성 전 필요한 실제 요청 수의 쿼터를 원자적으로 확보한다. 부족하면 외부 API를 호출하지 않고 전체 구간을 Haversine 예상시간으로 반환하며 warning을 남긴다.
- 일부 구간만 외부 경로이고 나머지만 fallback인 결과를 만들지 않는다. 사전 쿼터 부족과 기술 실패 fallback 정책은 일정 전체에서 일관되게 적용한다.
- DB 저장, 일정 Aggregate와 AI 기능을 구현하지 않는다.
