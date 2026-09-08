# User Domain Instructions

## Responsibility

- 회원, 인증 이후의 사용자 식별, 사용자가 소유한 여행 계획 접근 정책을 관리한다.

## Boundaries

- 회원·인증은 핵심 MVP 완료 후 범위를 확정한다. 요청되지 않은 로그인·소셜 로그인·권한 기능을 구현하지 않는다.
- 비밀번호는 평문으로 저장·로그·응답하지 않는다.
- JWT와 Spring Security 설정은 `global/security`에 두고, 사용자 도메인은 사용자 정책과 데이터만 소유한다.
- TravelPlan의 일정 생성·경로·추천 로직을 중복 구현하지 않는다.
