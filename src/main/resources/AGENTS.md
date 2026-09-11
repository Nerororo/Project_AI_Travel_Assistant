# Resources Harness

## Responsibility

애플리케이션 설정, 정적 자산, 정적 지역 데이터와 DB migration을 관리한다.

## Rules

- 설정 파일에 비밀값, 실제 API 키, JWT secret과 DB 비밀번호를 넣지 않는다.
- profile·로그·외부 연결·DB 변경 전 `docs/09-operations.md`를 확인한다.
- `data/regions.json`은 출처가 확인된 상위 서울·광역시·도와 최종 선택 가능한 시·군·구 기준 데이터만 포함하고 카카오 검색 결과를 복제하지 않는다.
- `static`은 브라우저 공개 자산이므로 서버 비밀과 내부 진단 정보를 포함하지 않는다.
- migration은 `docs/03-database.md` 계약을 따르고 적용된 versioned 파일을 수정하지 않는다.
- `api_usage_counters`와 `request_executions` migration에는 좌표·외부 payload·response 컬럼을 추가하지 않는다.
- 설정, 정적 데이터, migration과 화면 자산을 한 작업에 섞지 않는다.
