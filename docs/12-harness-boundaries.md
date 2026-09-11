# 하네스 경계표

## 1. 목적

이 문서는 `docs/11-command-roadmap.md`의 작업 ID별 쓰기 경계와 필수 참조를 정의한다. 루트와 대상 폴더의 `AGENTS.md`를 함께 적용하며, 경로는 프로젝트 루트 기준이고 `**`는 하위 전체를 뜻한다.

한 작업은 하나의 기능 또는 설계 변경만 다룬다. 표의 폴더 경로는 그 폴더 전체를 정리하라는 뜻이 아니며 Change Envelope에서 실제 파일 또는 가장 좁은 glob으로 제한해야 한다.

## 2. 도메인 소유권

| 대상 | 소유 책임 | 먼저 읽을 기준 | 금지되는 책임 |
|---|---|---|---|
| `user` | 회원, JWT, 인증 사용자, 호출 한도 기반 | API·DB·운영·테스트 | 여행 정책, 외부 장소·경로 호출 |
| `region` | 상위 서울·광역시·도와 최종 시·군·구 하나의 기준 데이터·검색 | 요구사항·API·ADR·테스트 | 상위 항목의 최종 일정 선택, 읍·면·동·해외, AI 호출 구현 |
| `ai` | 지역·메뉴 자연어 구조화와 OpenAI 통신 | API·운영·테스트 | 거리·순서·추천 점수·장소 검증 |
| `place` | 카카오 장소 검색, 숙소·음식점 지도 탐색, 체류 기본값, 선택 토큰 | 요구사항·API·운영·정책 ADR | 숙소 자동 점수 추천, 일정 배치, 경로 계산, 외부 응답 저장 |
| `route` | 순수 거리·순서 알고리즘과 이동 경로 Client | 요구사항·아키텍처·테스트·운영 | AI, DB 저장, 일정 Aggregate |
| `recommendation` | 음식점 시간 적합성·Haversine 이탈 점수와 정렬 | 요구사항·테스트 | 숙소 자동 순위, AI 최종 순위, Repository, 외부 Client 직접 생성 |
| `travelplan` | 일정 계산 조정, 완료 Aggregate와 API | API·DB·테스트·DoD | 다른 도메인 알고리즘·Client 중복 구현 |
| `global` | 공통 예외, 설정, 보안, 범용 도구 | API·운영 | 특정 여행 도메인 정책 |
| `resources/data` | 검증 가능한 정적 기준 데이터 | 데이터 출처 ADR·운영 | 카카오 검색 결과 복제 |
| `resources/db/migration` | versioned schema 변경 | DB·DoD | 적용된 migration 수정 |
| `static/Routy` | 실제 작성·완료·공유 화면 | 요구사항·API·테스트 | 백엔드 계약 우회, 외부 응답 영속화 |
| `travela-1.0.0` | 원본 템플릿 보관 | 해당 폴더 `AGENTS.md` | Routy 구현, 원본 변경·삭제 |
| `src/test` | 대응 코드 검증과 fixture | 테스트 전략 | 실제 외부 API 호출, 비밀·원문·실제 좌표 |

`preference` 도메인은 만들지 않는다. 장소 유형은 기본 체류 시간 계산에만 쓰고 노출·저장하지 않는다.

## 3. 경계 등급

| 등급 | 의미 |
|---|---|
| Allowed Paths | 현재 작업이 직접 소유하며 사전 설명한 경로 |
| Conditional Paths | 계약·설정 연동 때문에 조건부로 필요한 경로. 파일·조건·이유를 먼저 명시하고 승인받아야 함 |
| Forbidden Paths | 같은 작업에서 수정하거나 미승인 경로로 재분류할 수 없는 경로 |

표와 공통 Conditional 규칙에 없는 경로는 미승인 경로다. 필요하면 편집 전에 이유, 영향과 파일을 설명해 승인받는다. 명시적 Forbidden은 현재 작업을 끝내고 별도 작업으로 다룬다.

## 4. 단계별 쓰기 경계

축약한 `user/**` 같은 경로는 `src/main/java/com/example/travel/user/**`를 뜻한다.

| 단계 | Allowed Paths | Conditional Paths | Forbidden Paths |
|---|---|---|---|
| D0 문서·하네스 | 해당 작업에서 지정한 `docs/*.md`, D0-03의 `README.md`, D0-04의 루트·하위 `AGENTS.md` | 계약상 직접 필요한 다른 문서 | 애플리케이션 코드, 테스트, 설정, migration, 정적 자산, 원본 템플릿 |
| F0 개발 기반 | `build.gradle`, `settings.gradle`, `docker-compose.yaml`, `application*.yml`, 기반 `global/**`, 대응 기반 테스트 | 최초 versioned migration, 직접 관련된 운영·API·테스트 문서 | `ai/**`, `region/**`, `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, `user/**`, 정적 자산 |
| U1 인증·소유권 | `user/**`, `global/security/**`, 대응 테스트, 새 versioned migration | MySQL 호출·requestId 저장소, `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, 인증 관련 API·DB·ADR·테스트·운영 문서 | `ai/**`, `region/**`, `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, 정적 자산, 기존 migration |
| G1 국내 지역 | `region/**`, 대응 테스트, `src/main/resources/data/regions.json` | `ai`의 지역 추천 공개 계약, `global/exception/**`, `global/config/**`, OpenAI 설정·dependency, 데이터·API·ADR·테스트·운영 문서 | `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, migration, 정적 자산 |
| R1 순수 경로 | `route/algorithm/**`, 순수 정책·DTO, 대응 route 단위 테스트 | 추정 계수 ADR와 관련 요구사항·테스트 문서 | Spring Controller, Repository, HTTP Client, `ai/**`, `place/**`, `recommendation/**`, `travelplan/**`, migration, 정적 자산 |
| C1 Client 계약·Fake | `place/client` 인터페이스·Fake·전달 DTO, `route/client` 인터페이스·Fake·전달 DTO, 메뉴용 `ai` 계약·Fake, 대응 테스트 | 각 도메인의 얇은 Service 골격, 공통 오류 계약, 직접 관련된 API·ADR·테스트·운영 문서 | 실제 카카오 HTTP 호출, 좌표 기반 공개 endpoint, Entity·Repository·migration, 일정 구현, 정적 자산 |
| K0 답변 반영 | 답변 반영 대상으로 지정한 요구사항·API·ADR·테스트·운영·로드맵 문서 | `REMAKE.md`, 개인정보를 제거한 정책 기록 | 애플리케이션 코드, 테스트, 설정, migration, 정적 자산, 개인정보가 포함된 문의 원문의 공개 저장 |
| P1 카카오 장소 | `place/**`, 대응 place 테스트, 작성 화면의 승인된 장소 선택 모듈 | `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, `region`·`user` 공개 계약, 직접 관련 문서 | `route/**`, `recommendation/**`, `travelplan/**`, Entity·Repository·migration, 완료 화면, 원본 템플릿 |
| R2 독립 경로 기반 | `route/client/**`, `route/service/**`, 대응 route 테스트 | `place`·`user` 공개 DTO·Service 계약, `global/exception/**`, `global/config/**`, 설정·dependency, 관련 API·ADR·테스트·운영 문서 | `route/algorithm/**`의 무관한 변경, `ai/**`, `recommendation/**`, `travelplan/**`, Entity·Repository·migration, 정적 자산 |
| S1 추정 일정·추천 | `travelplan`의 계산 Service·정책·전달 DTO, `recommendation/**`, 대응 테스트 | `place`·`route/algorithm`·`region`의 공개 Service·DTO, `global/exception/**`, estimate API와 관련 요구사항·ADR·테스트 문서 | Entity·Repository·migration, `route/client/**`, `route/service/**`, `ai/**`, 완료·공유 API, 정적 자산 |
| T1 외부 경로 통합·완료 일정·API | `travelplan/**`, 대응 테스트, 새 versioned migration | `user`·`place`·`route`·`recommendation` 공개 계약, `global/exception/**`, DB·API·ADR·테스트·DoD 문서 | 외부 Client 내부 구현, `route/algorithm/**`, `ai/**`, 기존 migration, 정적 자산 |
| W1 실제 화면 | `src/main/resources/static/Routy/**`, 직접 대응 UI 테스트 | API 계약 오류가 확인된 경우 문서 변경 제안 | Java 코드, Repository, migration, 설정, `travela-1.0.0/**` |
| Q1 품질·운영 | `global/**`, 운영 관련 테스트, 승인된 `application*.yml`·Docker·배포 파일 | `build.gradle`, 각 도메인의 health용 공개 계약, 운영·DoD·준비도 문서 | 도메인 비즈니스 알고리즘, migration, 정적 화면, 원본 템플릿 |

### 확정된 카카오 데이터 수명 규칙

K0-02A는 2026-09-11 완료됐다. 다음 기능은 각 구현 단계의 Allowed Paths 안에서만 수정하며, 좌표의 일시 사용·즉시 폐기 조건을 필수로 적용한다.

- 실제 Kakao Local Client와 장소 검색 endpoint
- 카카오 검색 좌표를 받거나 전달하는 `selectionToken`
- 좌표 기반 estimate와 완료 일정 생성 endpoint
- 실제 카카오 자동차·대중교통 경로 호출
- 브라우저에서 카카오 좌표를 유지·전달하는 제작 흐름
- 위 기능의 운영 배포 또는 완료 표시

좌표와 검색 응답을 DB·Redis·Caffeine·서버 세션·브라우저 저장소·로그에 남기는 구현은 모든 단계에서 Forbidden이다.

## 5. 작업 ID 세부 경계

단계 표 안에서도 작업 ID의 명시적 목적이 우선한다.

- “설계” 작업은 문서와 읽기 전용 코드 조사만 Allowed다. 구현 파일은 수정하지 않는다.
- “구현” 작업은 로드맵에 적힌 결과에 직접 필요한 파일과 대응 테스트만 Allowed다.
- “회귀·점검” 작업은 기본적으로 읽기와 테스트 실행만 Allowed다. 발견한 문제 수정은 새 Change Envelope로 승인받는다.
- Entity 작업은 Controller를 포함하지 않는다.
- Client 작업은 Controller·Repository·Entity를 포함하지 않는다.
- 화면 작업은 백엔드 계약을 임시 데이터나 클라이언트 계산으로 우회하지 않는다.
- 문서 정렬은 코드가 존재하는 것처럼 구현 상태를 바꾸지 않는다.

## 6. 공통 Conditional 규칙

아래 항목도 자동 쓰기 권한이 아니다. 실제 파일과 조건을 Change Envelope에 적고 승인받는다.

- 대응 테스트: 변경 클래스·공개 계약에 직접 대응하는 `src/test/java/**`와 필요한 최소 `src/test/resources/**`
- `docs/06-decisions.md`: 설계 선택이나 기존 ADR 상태가 바뀔 때
- `docs/07-implementation-readiness.md`: 실제 구현 상태 또는 확인된 차단 조건이 바뀔 때
- `docs/08-test-strategy.md`: 테스트 책임이나 완료 검증 계약이 바뀔 때
- `docs/09-operations.md`: 설정·비밀값·쿼터·장애·배포 계약이 바뀔 때
- `docs/10-definition-of-done.md`: 완료 판정 조건이 바뀔 때
- `docs/11-command-roadmap.md`: 작업 순서, 이월 항목이나 현재 작업이 바뀔 때
- `docs/03-database.md`, `docs/04-api-spec.md`: DB 또는 공개 HTTP 계약이 실제로 바뀔 때
- `build.gradle`: 승인된 dependency 또는 빌드 규칙이 필요한 경우
- `application*.yml`: 승인된 profile·외부 연결·로그 설정이 필요한 경우

한 작업에서 관련 없는 문서와 테스트를 정리하지 않는다. `AGENTS.md`와 이 경계표의 변경은 D0의 별도 작업으로 다룬다.

## 7. Change Envelope 형식

모든 변경 작업은 편집 전에 다음을 알린다.

```text
Task ID:
Goal:
Allowed Paths:
Conditional Paths:
Forbidden Paths:
References Read:
Verification:
```

- Allowed Paths도 실제 수정 파일로 좁힌다.
- Conditional Paths에는 파일, 조건과 변경 이유를 함께 적는다.
- 기존 승인 밖의 dependency·schema·API 변경은 편집 전에 대안과 영향을 설명한다.
- 승인되지 않은 범위를 임시 상수, nullable 완화, 가짜 운영 데이터나 테스트 생략으로 우회하지 않는다.
- 작업 중 Forbidden 경로가 필요해지면 현재 작업을 끝내고 별도 작업으로 제안한다.

## 8. 변경 전 검사

1. 루트와 대상 폴더의 `AGENTS.md`를 읽는다.
2. 로드맵의 현재 작업 ID와 이 문서의 단계 경계를 확인한다.
3. 표에 지정된 관련 문서·endpoint·절만 읽는다.
4. `git status --short`와 대상 파일의 기존 diff를 확인한다.
5. 사용자 변경과 현재 작업 변경을 구분한다.
6. Change Envelope를 설명한다.
7. 필요한 승인 뒤 편집한다.

설명만 요청된 작업에서는 파일을 수정하지 않는다.

## 9. 변경 후 검사

1. 실제 수정 파일을 Allowed·승인된 Conditional Paths와 대조한다.
2. 작업 전부터 dirty인 파일의 기존 변경을 보존했는지 확인한다.
3. `git status --short`와 `git diff --name-only`로 새 범위 밖 변경을 확인한다.
4. 관련 단위·통합·HTTP 테스트를 실행한다.
5. `./gradlew test`를 실행한다.
6. `git diff --check`로 공백 오류를 확인한다.
7. `docs/10-definition-of-done.md`의 공통·도메인 항목을 확인한다.
8. 미검증·차단·조건부 항목을 완료로 표시하지 않는다.

문서 전용 작업은 Gradle 테스트를 생략할 수 있지만 이유와 미검증 범위를 기록한다. 검사 목적으로 사용자 변경을 stash, reset, checkout하거나 삭제하지 않는다.

## 10. 외부 데이터와 비밀값 경계

- 카카오 좌표·주소·전화번호·카테고리·장소명·원문과 경로 원문을 DB, cache, 서버 session, 로그와 fixture에 저장하지 않는다.
- 작성 중 좌표의 브라우저 메모리 사용은 K0 허용 조건 안에서만 가능하며 localStorage·sessionStorage·IndexedDB에 저장하지 않는다.
- 중복 요청 저장소에는 사용자와 `requestId`의 식별 정보 및 상태만 10분간 두고 payload·response를 저장하지 않는다.
- API 키, JWT secret, 비밀번호와 Authorization 헤더를 코드·Git·로그·오류 응답에 남기지 않는다.
- 자동 테스트에서 실제 OpenAI·카카오 API를 호출하지 않는다.
- 완료·공유 조회는 외부 API를 호출하지 않는다.
