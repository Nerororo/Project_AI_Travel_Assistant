# 구현 준비도와 현재 기준선

## 1. 문서 역할

이 문서는 현재 코드와 미확정 사항을 빠르게 파악하기 위한 기준선이다. 요구사항이나 완료 체크리스트를 복제하지 않는다.

- 기능·제약의 기준: `docs/01-requirements.md`
- 구조의 기준: `docs/02-architecture.md`
- DB·API 계약: `docs/03-database.md`, `docs/04-api-spec.md`
- 실행 순서: `docs/11-command-roadmap.md`
- 완료 판정: `docs/10-definition-of-done.md`

## 2. 현재 기준선 (2026-09-07)

| 항목 | 상태 | 근거 / 다음 조치 |
|---|---|---|
| Spring Boot 웹 애플리케이션 | 기본 골격 | `TravelApplication`과 `GET /hello`가 있다. |
| Java 21 / Gradle | 준비됨 | Java toolchain과 Gradle wrapper가 있다. |
| MySQL 컨테이너 | 구성됨 | `docker-compose.yaml`에 MySQL 8.4 서비스가 있다. |
| JPA / migration | 설정 초안만 존재 | `application.yml`에 datasource와 `ddl-auto: validate` 초안은 있으나 JPA, MySQL driver, Flyway dependency와 migration은 아직 추가해야 한다. |
| 핵심 도메인 | 구현 전 | Google Place 참조, Route, TravelPlan, Recommendation은 문서만 준비돼 있다. |
| Google Maps Platform | 구현 전 | Places·Routes·Maps JavaScript API 계약과 키 제한을 확인한 뒤 fake client부터 구현한다. |
| OpenAI 연동 | 구현 전 | 국가 기반 도시 후보 계약을 확인한 뒤 fake client부터 구현한다. |
| 인증 | 후순위 | 핵심 MVP 이후 U1 단계에서 범위를 확정한다. |

이 표는 구현 상태가 바뀔 때만 갱신한다.

## 3. 구현 전 확인할 미확정 사항

| 주제 | 확인 시점 | 기록할 기준 문서 |
|---|---|---|
| Google Places Field Mask·캐시 허용 범위·API 키 제한 | Place 구현 전 | `docs/04-api-spec.md`, `docs/09-operations.md` |
| Google Routes route matrix 한도·이동 방식·원소별 실패·Field Mask | R1-06 구현 전 | `docs/04-api-spec.md`, `docs/09-operations.md` |
| AI 도시 후보 schema와 provider 설정 | AI 연동 전 | `docs/04-api-spec.md`, `docs/09-operations.md` |
| 일정 입력 제약, 장소 유형별 체류 시간, 일일 시간 용량 | TravelPlan 구현 전 | `docs/01-requirements.md`, `docs/04-api-spec.md` |
| 상태·오류 코드와 paging 계약 | 각 API 구현 전 | `docs/04-api-spec.md` |
| 배포 플랫폼과 health endpoint | 운영 단계 전 | `docs/09-operations.md` |

결정이 기존 설계를 바꾸면 `docs/06-decisions.md`에도 이유를 기록한다.

## 4. 현재 다음 작업

현재 구현 시작점은 `docs/11-command-roadmap.md`의 **F0-01**이다. 이후에는 로드맵의 작업 ID를 한 번에 하나씩 진행한다.

작업의 완료 여부는 이 문서가 아니라 `docs/10-definition-of-done.md`로 판단한다.
