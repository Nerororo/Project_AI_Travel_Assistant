# 개발 계획

## 1. 문서 역할

이 문서는 프로젝트의 큰 개발 방향과 단계별 학습 목표만 관리한다.

- 실제 작업 순서와 현재 작업: `docs/11-command-roadmap.md`
- 기능 완료 판정: `docs/10-definition-of-done.md`
- 테스트 방법과 범위: `docs/08-test-strategy.md`
- 변경된 설계 결정: `docs/06-decisions.md`

작업 상태, 세부 구현 목록, 완료 체크리스트를 이 문서에 중복 기록하지 않는다.

## 2. 개발 전략

Spring Boot를 학습하면서 포트폴리오 수준의 서비스를 완성한다. 한 번에 전체 기능을 구현하지 않고 다음 흐름을 반복한다.

```text
작은 작업 선택 → 설계와 개념 이해 → 구현 → 테스트 → 완료 기준 확인 → 다음 작업
```

- 한 번에 `docs/11-command-roadmap.md`의 작업 ID 하나만 진행한다.
- 새로운 Spring 개념은 구현 전에 역할과 동작 흐름을 이해한다.
- 측정하지 않은 성능이나 품질 수치를 문서와 포트폴리오에 사용하지 않는다.

## 3. 단계별 목표

| 단계 | 목표 | 주요 학습 내용 | 실행 작업 |
|---|---|---|---|
| 개발 기반 | JPA·MySQL·profile 기반 준비 | Dependency, Configuration, migration | F0 |
| Place | Google 장소 검색과 내부 참조 저장 | Entity, Repository, 외부 Client, DTO, Validation | P1 |
| 거리·경로 | 거리·정적 이동 시간 행렬과 방문 순서 결정 | 순수 Java 로직, Haversine, Nearest Neighbor, Google Routes client 분리 | R1 |
| TravelPlan 저장 | 일정 Aggregate 저장과 조회 | 연관관계, Transaction, Lazy Loading | T1 |
| AI 도시 추천 | 국가 기반 도시 후보를 제한된 DTO로 생성 | 외부 Client 분리, 구조화 응답, Google 검증 | A1 |
| 추천 | 장소·호텔·식사 시간대별 음식점 후보 평가 | 점수, 내부·인접 판정, 정렬, Service 책임 분리 | R2 |
| 최종 일정 | 체류·이동·식사 시간을 조합해 일정 생성 | 시간 용량, Orchestration, transaction, 통합 테스트 | T2 |
| 품질·운영 | 오류·profile·배포 기반 정리 | Exception Handler, logging, health check | Q1 |
| 인증 | 사용자와 일정 소유권 보호 | Spring Security, JWT | U1 |
| 서비스 화면 | 브라우저에서 전체 사용자 흐름 완성 | 상태 관리, API 연동, 지도, 접근성, 오류 UX | S1 |

2-opt 같은 품질 개선은 기본 경로 기능이 안정된 뒤 별도 작업으로 추가한다.

화면은 백엔드 전체 완료 뒤 한꺼번에 연결하지 않는다. F0 뒤에 화면 구조를 정하고, 관련 공개 API가 안정되는 시점마다 목적지 선택, 여행 조건·후보 선택, 일정 결과·수정·음식점 검색, 인증·내 일정 순으로 얇게 연결한다. 각 연결 단계는 fake 또는 테스트 환경에서 브라우저로 확인하며 최종 S1 회귀에서 하나의 사용자 흐름으로 검증한다.

## 4. 작업 루틴

1. `docs/11-command-roadmap.md`에서 작업 ID 하나를 선택한다.
2. `docs/00-docs-index.md`의 선택적 읽기 표에 따라 필요한 문서만 확인한다.
3. 수정할 파일, 각 클래스의 책임, 새 Spring 개념을 확인한다.
4. 구현하고 `docs/08-test-strategy.md`에 맞는 테스트를 실행한다.
5. `docs/10-definition-of-done.md`의 공통 항목과 해당 도메인 항목을 확인한다.
6. 계약이 바뀐 문서만 갱신하고, 새 설계 결정은 `docs/06-decisions.md`에 기록한다.

## 5. 포트폴리오용 측정 항목

- 경로: Nearest Neighbor 거리, 2-opt 적용 전후 거리와 개선율
- AI: 도시 후보 parsing·Google 검증 결과, 실패 유형, 재시도 결과
- DB: 주요 query, N+1 발생 여부, 개선 전후 결과
- 테스트: 핵심 domain, service, integration test 결과

실제 측정 결과만 README와 포트폴리오에 기록한다.
