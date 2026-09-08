# 하네스 경계표

## 목적

이 문서는 Routy의 작업 경계와 필수 참조 위치를 빠르게 확인하기 위한 기준이다. 세부 규칙은 루트 및 각 폴더의 `AGENTS.md`와 함께 적용한다. 경로는 프로젝트 루트 기준이며 `**`는 하위 전체를 뜻한다.

| 작업 대상 | 소유 역할 | 먼저 확인할 기준 | 직접 변경하지 않는 항목 |
|---|---|---|---|
| `ai` | 국가 기반 도시 후보 생성과 OpenAI 통신 | `04-api-spec.md`, `09-operations.md` | Google Place ID 생성·장소 검증·경로 결정 |
| `place` | Google Places 통신·장소 검증·Place ID 참조 저장 | `03-database.md`, `04-api-spec.md` | 일정 생성·AI 호출·Google 콘텐츠 무단 영구 저장 |
| `route` | 거리·이동 시간 행렬과 방문 순서 알고리즘, Google Routes 통신 | `01-requirements.md`, `08-test-strategy.md`, `09-operations.md` | `algorithm`의 Spring·DB·AI·HTTP 의존 코드 |
| `recommendation` | 장소·호텔·식사 시간대별 음식점 후보 점수 | `01-requirements.md`, `08-test-strategy.md` | AI의 최종 순위 결정·Google Routes 직접 호출 |
| `travelplan` | 일정 CRUD와 체류·이동·식사 시간 조합 | `04-api-spec.md`, `08-test-strategy.md` | 하위 도메인 알고리즘·외부 Client 중복 구현 |
| `user` | 회원·사용자 소유권 정책 | `01-requirements.md`, `04-api-spec.md` | 핵심 MVP 이전의 인증 구현 |
| `global` | 공통 예외·설정·보안 | `04-api-spec.md`, `09-operations.md` | 도메인별 여행 정책 |
| `resources` | 설정·정적 자산·migration | `03-database.md`, `09-operations.md` | 비밀값·무관한 템플릿 변경 |
| `static/Routy` | 실제 서비스 화면 | `01-requirements.md`, `04-api-spec.md` | 백엔드 알고리즘·외부 라이브러리 직접 수정 |
| `travela-1.0.0` | 원본 템플릿 보관 | 해당 폴더 `AGENTS.md` | Routy 기능 구현·원본 자산 삭제 |
| `src/test` | 테스트·fixture | `08-test-strategy.md` | 실제 외부 API 호출·민감 정보 |

여행 취향과 혼잡도는 입력받거나 저장하지 않으며 `preference` 도메인을 만들지 않는다. AI 도시 후보 DTO와 OpenAI 통신은 `ai`, Google 국가·도시·장소 검증은 `place`, 선택된 도시와 방문 장소 참조는 `travelplan`이 소유한다.

## 경계 등급

| 등급 | 의미 | 작업 규칙 |
|---|---|---|
| Allowed Paths | 승인된 작업의 주 소유 경로 | 사전 설명한 파일만 수정한다. 같은 폴더라는 이유로 전체 파일을 정리하지 않는다. |
| Conditional Paths | 계약·설정·연동 때문에 함께 바뀔 수 있는 경로 | Change Envelope에 파일과 이유를 먼저 적고 승인받은 경우에만 수정한다. |
| Forbidden Paths | 단계표·적용 규칙·Change Envelope에서 명시적으로 금지한 경로 | 같은 작업에서 수정하거나 미승인 경로로 재분류하지 않는다. 필요하면 별도 작업으로 제안한다. |

표와 아래 공통 Conditional 규칙에 없는 경로는 미승인 경로다. 읽기 전용 확인은 가능하지만 수정은 허용되지 않는다. 현재 작업에 필요하면 이유·영향·파일 목록을 설명하고 사용자 승인을 받은 뒤 Change Envelope에 추가할 수 있다. 명시적 Forbidden에는 이 추가 절차를 적용하지 않는다.

## 단계별 쓰기 경계

| 단계 | Allowed Paths | Conditional Paths | Forbidden Paths |
|---|---|---|---|
| F0 개발 기반 | `build.gradle`, `settings.gradle`, `docker-compose.yaml`, `src/main/resources/application*.yml`, 기반 설정 관련 테스트 | `src/main/resources/db/migration/**`, 직접 관련된 `docs/*.md` | `src/main/java/com/example/travel/{ai,place,route,recommendation,travelplan,user}/**`, `src/main/resources/static/**`, 원본 템플릿 |
| P1 Google 장소 | `src/main/java/com/example/travel/place/**`, 대응하는 `src/test/**/place/**` | `global/exception/**`, `global/config/**`, migration, `build.gradle`, `application*.yml`, `docs/03-database.md`, `docs/04-api-spec.md`, `docs/08-test-strategy.md`, `docs/09-operations.md`, `docs/10-definition-of-done.md` | `ai/**`, `route/**`, `recommendation/**`, `travelplan/**`, `user/**`, `static/**`, 원본 템플릿 |
| R1 거리·경로 | `src/main/java/com/example/travel/route/**`, 대응하는 `src/test/**/route/**` | `place`의 공개 Service·DTO 계약, `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, 직접 관련된 요구사항·API·테스트·운영 문서 | `ai/**`, `recommendation/**`, `travelplan/**`, `user/**`, `static/**`, 원본 템플릿 |
| T1 계획 저장 | `src/main/java/com/example/travel/travelplan/**`, 대응하는 `src/test/**/travelplan/**`, 새 versioned migration | `place`의 공개 Service·DTO 계약, `global/exception/**`, `docs/03-database.md`, `docs/04-api-spec.md`, `docs/08-test-strategy.md`, `docs/10-definition-of-done.md` | `ai/**`, `route/algorithm/**`, `recommendation/**`, `user/**`, `static/**`, 기존 migration, 원본 템플릿 |
| A1 AI 도시 | `src/main/java/com/example/travel/ai/**`, 대응하는 `src/test/**/ai/**` | `place`의 공개 Service·DTO 계약, `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, 직접 관련된 API·테스트·운영 문서 | `route/**`, `recommendation/**`, `travelplan/domain/**`, `user/**`, `static/**`, migration, 원본 템플릿 |
| R2 추천 | `src/main/java/com/example/travel/recommendation/**`, 대응하는 `src/test/**/recommendation/**` | `place`·`route`의 공개 Service·DTO 계약, `global/exception/**`, 직접 관련된 요구사항·API·테스트 문서 | `ai/**`, `route/algorithm/**`, `travelplan/domain/**`, `user/**`, `static/**`, migration, 원본 템플릿 |
| T2 최종 일정 | `src/main/java/com/example/travel/travelplan/**`, 대응하는 `src/test/**/travelplan/**` | `place`·`route`·`recommendation`의 공개 Service·DTO 계약, `global/exception/**`, 새 versioned migration, 직접 관련된 요구사항·DB·API·테스트·ADR·완료 문서 | 외부 Client 내부 구현, `route/algorithm/**`, `ai/**`, `user/**`, `static/**`, 기존 migration, 원본 템플릿 |
| Q1 품질·운영 | `src/main/java/com/example/travel/global/**`, 운영 관련 `src/test/**`, 승인된 설정·Docker 파일 | `build.gradle`, 각 도메인의 health 노출용 공개 계약, `docs/09-operations.md`, `docs/10-definition-of-done.md` | 도메인 비즈니스 알고리즘, `static/**`, migration, 원본 템플릿 |
| U1 인증 | `src/main/java/com/example/travel/user/**`, `global/security/**`, 대응하는 테스트, 새 versioned migration | TravelPlan 소유권 확인에 필요한 공개 Service·Entity 계약, `build.gradle`, `application*.yml`, 직접 관련된 요구사항·DB·API·테스트·운영 문서 | `ai/**`, `place/client/**`, `route/**`, `recommendation/**`, `static/**`, 기존 migration, 원본 템플릿 |
| UI 화면 | `src/main/resources/static/Routy/**`, 대응하는 UI 테스트 | 백엔드 API 계약 확인을 위한 `docs/04-api-spec.md` 변경 제안 | Java 도메인·Repository·migration·설정, `travela-1.0.0/**` 원본 템플릿 |
| 문서 전용 | 요청에서 지정한 문서와 그 계약에 직접 필요한 `docs/*.md` | `README.md`, 루트·하위 `AGENTS.md`, 다른 기준 문서 | 애플리케이션 코드, 테스트, 설정, migration, 정적 자산 |

`place`처럼 축약한 경로는 `src/main/java/com/example/travel/place/**`를 뜻한다. Conditional 경로도 폴더 전체 수정 허가가 아니며 Change Envelope에서 실제 파일까지 좁혀야 한다.

## Change Envelope

### 모든 단계의 공통 Conditional 규칙

아래 경로는 단계표의 Conditional 후보를 보완한다. 자동 수정 권한이 아니며 실제 파일과 조건을 Change Envelope에 적고 승인받아야 한다. 단계표나 적용 규칙에서 명시적으로 금지한 경로가 있으면 그 금지가 우선한다.

- 허용·승인된 코드 변경을 검증하는 대응 테스트: 해당 클래스·공개 계약에 직접 대응하는 `src/test/java/**`의 테스트 파일과 필요한 `src/test/resources/**` fixture·테스트 설정만 포함한다. 예를 들어 P1에서 `global/exception`을 변경하면 `src/test/java/com/example/travel/global/exception/**` 중 해당 예외 처리 테스트를 지정할 수 있다. 다른 도메인의 공개 계약을 변경할 때도 그 계약의 대응 테스트를 지정한다. 문서 전용 작업처럼 코드·테스트가 Forbidden인 단계에는 적용하지 않는다.
- `docs/06-decisions.md`: 현재 작업에서 설계 선택이 바뀌어 이유나 기존 결정의 상태를 기록할 때만 지정한다.
- `docs/07-implementation-readiness.md`: 실제 구현 상태 또는 확인된 미확정 사항이 바뀔 때만 지정한다.
- `docs/11-command-roadmap.md`: 현재 작업의 진행 상태·이월 항목·직접 영향받은 작업 순서를 기록할 때만 지정한다.

문서와 테스트의 다른 항목을 함께 정리하지 않는다. `AGENTS.md`와 이 경계표 자체의 변경은 계속 별도 하네스 작업으로 다룬다.

### 작업별 선언

모든 구현 작업은 편집 전에 다음 내용을 사용자에게 알린다.

```text
Task ID: 로드맵 ID 또는 사용자가 지정한 작업명
Goal: 이번 작업에서 완료할 한 가지 결과
Allowed Paths: 실제 수정할 파일 또는 가장 좁은 glob
Conditional Paths: 필요할 수 있는 파일과 변경 조건
Forbidden Paths: 이번 작업에서 건드리지 않을 주요 경로
References Read: 먼저 확인한 기준 문서와 AGENTS.md
Verification: 실행할 단위·통합·전체 테스트와 경계 검사
```

- 설명 단계에서는 파일을 수정하지 않는다.
- 사용자가 구현을 승인하면 최초 또는 추가 승인된 Allowed와 조건이 충족된 Conditional 범위만 수정한다.
- 작업 중 미승인 경로가 필요해지면 해당 파일의 편집 전에 이유·영향·추가 경로와 조건을 제시한다. 사용자 승인 후 Change Envelope를 갱신하고 진행한다. 명시적 Forbidden은 별도 작업으로 분리한다.
- 기존 승인에 없던 dependency·schema·API 변경이 필요해지면 편집 전에 이유·대안·영향을 설명하고 승인받는다.
- 승인받지 못한 범위는 현재 구현에서 제외하며 임시 우회 코드를 만들지 않는다.

## 변경 전후 검사

### 작업 전

1. 루트와 대상 폴더의 `AGENTS.md`를 읽는다.
2. `git status --short`와 대상 파일의 기존 diff를 확인한다.
3. 기존 사용자 변경과 이번 작업의 변경을 구분한다.
4. Change Envelope를 설명하고 구현 승인을 확인한다.

### 작업 후

1. 실제로 편집한 파일 목록을 기록하고 Allowed·승인된 Conditional Paths와 대조한다.
2. 작업 전부터 dirty였던 파일은 기존 변경을 보존했는지 diff로 확인한다.
3. 새로 나타난 범위 밖 파일이 없는지 `git status --short`와 `git diff --name-only`로 확인한다.
4. 관련 테스트와 `./gradlew test`를 실행하고 결과를 기록한다. 문서 전용 작업이면 테스트 생략 이유를 기록한다.
5. `git diff --check`로 공백 오류를 확인한다.
6. 범위 위반이 발견되면 완료로 표시하지 않고 사용자에게 파일과 원인을 알린다.

검사를 위해 사용자 변경을 stash, reset, checkout 또는 삭제하지 않는다. 사용자가 요청하지 않으면 staging과 commit도 하지 않는다.

## 작업 순서

1. 수정 대상 폴더의 `AGENTS.md`와 표의 기준 문서를 읽는다.
2. 작업 전 dirty 상태를 확인하고 Change Envelope를 설명한다.
3. 승인된 Allowed·Conditional Paths 안에서 하나의 기능 또는 설계 변경만 구현한다.
4. 실제 변경 파일의 경계 위반 여부를 검사한다.
5. 관련 테스트와 `./gradlew test`를 실행한다.
6. 해당 Definition of Done을 확인한 뒤 승인 범위의 관련 문서만 갱신한다.
