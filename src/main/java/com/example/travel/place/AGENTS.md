# Place Domain Instructions

이 패키지는 장소 데이터를 담당한다.

Place는 관광지, 호텔, 음식점을 통합하여 표현한다.

PlaceType을 이용해 장소 종류를 구분한다.

예:

ATTRACTION
HOTEL
RESTAURANT
CAFE


## Responsibilities

place 패키지는 다음 역할만 담당한다.

- 장소 등록
- 장소 조회
- 장소 검색
- 장소 데이터 저장


## Rules

여행 일정 생성 로직을 PlaceService에 작성하지 않는다.

경로 최적화 로직을 작성하지 않는다.

AI API를 직접 호출하지 않는다.

Controller는 Entity를 직접 반환하지 않는다.

DTO를 사용한다.