# Resources Harness

## Responsibility

- 애플리케이션 설정, 정적 자산, 템플릿, 향후 DB migration을 관리한다.

## Boundaries

- 설정 파일에는 비밀값, 실제 API key, DB 비밀번호를 넣지 않는다.
- profile, 로그, 외부 API, DB 연결 변경은 `docs/09-operations.md`를 먼저 확인한다.
- `static`은 브라우저에 그대로 제공되는 자산이다. 서버 비밀값이나 내부 API 구현 세부 사항을 포함하지 않는다.
- migration을 추가하거나 변경할 때는 `docs/03-database.md`와 루트 `AGENTS.md`의 migration 규칙을 함께 따른다.
