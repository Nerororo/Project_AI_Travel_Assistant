# Route Domain Instructions

route 패키지는 여행 이동 경로 계산을 담당한다.

AI API에 의존하지 않는다.


## Responsibilities

- 두 장소 사이 거리 계산
- 거리 Matrix 생성
- 방문 순서 결정
- 총 이동거리 계산


## Algorithm

첫 구현에서는 Nearest Neighbor를 사용한다.

초기 구현이 완료되면
필요한 경우 2-opt 알고리즘을 추가한다.


## Development Rule

알고리즘 코드를 작성할 때
시간 복잡도를 설명한다.

알고리즘 변경 전후의 결과를
테스트로 비교할 수 있도록 한다.