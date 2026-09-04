# Global Package Instructions

global은 여러 도메인에서 공통으로 사용하는 기능만 포함한다.

## Allowed

- Exception Handling
- Configuration
- Security
- Common Response
- 공통 Utility


## Not Allowed

특정 도메인 비즈니스 로직을
global에 작성하지 않는다.

예:

여행 추천
여행 일정 계산
호텔 추천

등은 global에 두지 않는다.