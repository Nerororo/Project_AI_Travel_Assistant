# Global Package Instructions

## Responsibility

여러 도메인이 공유하는 예외, 설정, 보안과 범용 도구를 관리한다.

## Rules

- 여행 추천, 장소 정책, 일정 계산과 경로 알고리즘을 두지 않는다.
- 특정 도메인에서만 쓰는 코드는 해당 패키지에 둔다.
- 오류 응답은 `docs/04-api-spec.md`, 로그·비밀값·profile·health는 `docs/09-operations.md`를 따른다.
- Security 설정은 인증 사용자 전달과 endpoint 보호를 담당하고 사용자 비즈니스 정책은 user에 둔다.
- 한도 설정을 코드에 흩어 놓지 않고 설정과 정책 클래스에서 관리한다.
- 호출 카운터와 requestId 상태는 user가 소유한 공개 Service·DTO를 사용한다. global은 관련 설정과 공통 오류 변환만 담당하며 저장소를 직접 소유하지 않는다. 외부 호출을 DB 트랜잭션 안에서 수행하지 않는다.
- 처리 중 같은 requestId는 409 `REQUEST_IN_PROGRESS`, 성공한 같은 requestId는 409 `REQUEST_ALREADY_COMPLETED`로 변환하며 두 경우 모두 외부 호출·저장·호출량 차감을 반복하지 않는다.
- 일반 사용자 호출 한도 초과는 429와 재시도 정보를 반환한다. 완료 생성의 경로 쿼터 부족은 예외적으로 전체 Haversine fallback과 warning 계약을 따르며 429로 바꾸지 않는다.
- 공통 오류 응답의 `code`, `message`, `details`, `adjustments`, `retryAfterSeconds`는 API 문서 의미를 유지한다.
- 로그에 비밀값, 개인정보, 사용자 원문, 외부 원문, 좌표와 token을 남기지 않는다.
- health 요청마다 외부 API를 실제 호출하지 않는다.
