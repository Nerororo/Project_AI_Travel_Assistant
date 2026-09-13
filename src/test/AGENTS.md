# Test Harness

## Responsibility

자동 테스트와 테스트 전용 fixture를 관리한다.

## Rules

- 책임과 필수 사례는 `docs/08-test-strategy.md`, 완료 판정은 `docs/10-definition-of-done.md`를 따른다.
- 실제 OpenAI·카카오 API를 호출하지 않고 Fake 또는 mock을 사용한다.
- 단위 테스트는 순수 규칙, Service 테스트는 use case, 통합 테스트는 Repository·migration·DB 제약, API 테스트는 인증·validation·상태 코드를 검증한다.
- 테스트는 순서·공유 상태·실제 시계에 의존하지 않는다. 시간 규칙은 고정 `Clock`과 `Asia/Seoul`을 사용한다.
- 비밀값, 개인정보, 사용자 원문, 외부 원문, 실제 카카오 좌표와 token 원문을 fixture·assertion·실패 출력에 넣지 않는다.
- 카카오 DevTalk의 일시 사용 허용 답변과 Fake 테스트 통과만으로 운영 가능하다고 판단하지 않는다. 실제 구현의 즉시 폐기와 저장 금지를 별도로 검증한다.
- 도메인별 필수 사례, 경계값과 브라우저 흐름은 `docs/08-test-strategy.md`의 해당 절을 단일 기준으로 사용하고 이 파일에 복제하지 않는다.
- 변경 코드에 직접 대응하는 최소 테스트만 수정한다.
