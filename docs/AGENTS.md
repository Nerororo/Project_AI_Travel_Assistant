# Documentation Instructions

## Responsibility

- `docs/`는 요구사항, 아키텍처, DB, API, 테스트, 운영, 완료 기준의 기준 문서를 관리한다.

## Boundaries

- 현재 작업과 직접 관련된 문서만 갱신한다. 계획과 구현 완료 상태를 혼동하지 않는다.
- 애플리케이션 작업에서 `docs/**`는 자동 Allowed가 아니다. 실제로 바뀌는 계약 문서와 현재 작업에 필요한 설계 결정·구현 상태·현재 작업 기록을 `12-harness-boundaries.md`의 공통 Conditional 규칙에 따라 Change Envelope에 파일 단위로 적고 승인받는다.
- 문서 하나를 고친다는 이유로 인덱스·README·전체 문서를 일괄 정리하지 않는다.
- 하네스 자체를 변경하는 작업이 아니면 `AGENTS.md`와 `12-harness-boundaries.md`를 수정하지 않는다.
- API 계약은 `04-api-spec.md`, DB 계약은 `03-database.md`, 설계 선택 이유는 `06-decisions.md`, 구현 준비도는 `07-implementation-readiness.md`에 기록한다.
- 기능 완료 표시는 `10-definition-of-done.md` 확인 뒤에만 갱신한다.
- 문서 구조나 번호를 임의로 재배치하지 않는다. 새 기준 문서를 추가하면 `00-docs-index.md`에 목적을 등록한다.
