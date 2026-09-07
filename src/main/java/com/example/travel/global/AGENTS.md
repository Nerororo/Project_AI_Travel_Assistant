# Global Package Instructions

## Responsibility

- 여러 도메인이 공유하는 예외 처리와 공통 응답을 관리한다.
- 공통 Configuration, Security, 범용 Utility를 관리한다.

## Package Rules

- 여행 추천, 일정 계산, 장소 추천 같은 도메인 로직을 두지 않는다.
- 특정 도메인에서만 쓰는 코드는 해당 도메인 패키지에 둔다.
- 오류 응답 계약은 `docs/04-api-spec.md`를 따른다.
- 로그와 보안 설정은 `docs/09-operations.md`를 따른다.
