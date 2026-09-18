# 하네스 경계표

## 1. 목적

이 문서는 `docs/11-command-roadmap.md`의 작업 ID별 쓰기 경계와 필수 참조를 정의한다. 루트와 대상 폴더의 `AGENTS.md`를 함께 적용하며, 경로는 프로젝트 루트 기준이고 `**`는 하위 전체를 뜻한다.

한 작업은 하나의 기능 또는 설계 변경만 다룬다. 표의 폴더 경로는 그 폴더 전체를 정리하라는 뜻이 아니며 Change Envelope에서 실제 파일 또는 가장 좁은 glob으로 제한해야 한다.

## 2. 도메인 소유권

| 대상 | 소유 책임 | 먼저 읽을 기준 | 금지되는 책임 |
|---|---|---|---|
| `user` | 회원, JWT, 인증 사용자, 호출 한도 기반 | API·DB·운영·테스트 | 여행 정책, 외부 장소·경로 호출 |
| `region` | 자체 선택 가능한 서울·광역시·세종, 상위 도·특별자치도와 최종 시·군, 특별시·광역시의 검색 필터용 구·군 기준 데이터·검색 | 요구사항·API·ADR·테스트 | 도·특별자치도의 최종 일정 선택, 특별시·광역시 구·군의 최종 일정 선택, 읍·면·동·해외, AI 호출 구현 |
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
| D0 문서·하네스 | 해당 정렬 작업에서 지정한 `docs/*.md`, `README.md`, 루트·하위 `AGENTS.md` 중 사전 승인한 파일 | 계약상 직접 필요한 다른 문서 | 애플리케이션 코드, 테스트, 설정, migration, 정적 자산, 원본 템플릿 |
| F0 개발 기반 | `build.gradle`, `settings.gradle`, `docker-compose.yaml`, `application*.yml`, `application*.properties`, 기반 `global/**`, 대응 기반 테스트 | 최초 versioned migration, 직접 관련된 운영·API·테스트 문서 | `ai/**`, `region/**`, `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, `user/**`, 정적 자산 |
| U1 인증·소유권 | `user/**`, `global/security/**`, 대응 테스트, 새 versioned migration | MySQL 호출·requestId 저장소와 처리 중·성공 중복 409 계약, `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, `application*.properties`, 인증 관련 API·DB·ADR·테스트·운영 문서 | `ai/**`, `region/**`, `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, 정적 자산, 기존 migration |
| G1 국내 지역 | `region/**`, 대응 테스트, `src/main/resources/data/regions.json` | `global/exception/**`, 데이터·API·ADR·테스트·운영 문서 | `ai/**`, `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, migration, 정적 자산 |
| A1 지역·메뉴 AI | `ai/**`, 대응 ai 테스트 | `region`·`user` 공개 Service·DTO, `global/exception/**`, `global/config/**`, `application*.yml`, `application*.properties`, OpenAI dependency, 직접 관련된 API·ADR·테스트·운영 문서 | `place/**`, `route/**`, `recommendation/**`, `travelplan/**`, Entity·Repository·migration, 정적 자산 |
| R1 순수 경로 | `route/algorithm/**`, 순수 정책·DTO, 대응 route 단위 테스트 | 추정 계수 ADR와 관련 요구사항·테스트 문서 | Spring Controller, Repository, HTTP Client, `ai/**`, `place/**`, `recommendation/**`, `travelplan/**`, migration, 정적 자산 |
| C1 Place·Route Client 계약·Fake | `place/client` 인터페이스·Fake·전달 DTO, `route/client` 인터페이스·Fake·전달 DTO, 대응 테스트 | 각 도메인의 얇은 Service 골격, 공통 오류 계약, 직접 관련된 API·ADR·테스트·운영 문서 | `ai/**`, 실제 카카오 HTTP 호출, 좌표 기반 공개 endpoint, Entity·Repository·migration, 일정 구현, 정적 자산 |
| K0 답변 반영 | 답변 반영 대상으로 지정한 요구사항·API·ADR·테스트·운영·로드맵 문서 | `REMAKE.md`, 개인정보를 제거한 정책 기록 | 애플리케이션 코드, 테스트, 설정, migration, 정적 자산, 개인정보가 포함된 문의 원문의 공개 저장 |
| P1 카카오 장소 | `place/**`, 대응 place 테스트, 작성 화면의 승인된 브라우저 메모리 상태 모듈·장소 임시 상태 전달 adapter·직접 대응 순수 상태 테스트 | `global/exception/**`, `global/config/**`, `build.gradle`, `application*.yml`, `application*.properties`, `region`·`user` 공개 계약, 직접 관련 문서 | `route/**`, `recommendation/**`, `travelplan/**`, Entity·Repository·migration, 전체 화면 구조·표현·지도·마커 렌더링, 메뉴·식사·일정·음식점 화면 상태, 완료 화면, 원본 템플릿 |
| R2 독립 경로 기반 | `route/client/**`, `route/service/**`, 대응 route 테스트 | `place`·`user` 공개 DTO·Service 계약, `global/exception/**`, `global/config/**`, `application*.yml`, `application*.properties`, dependency, 관련 API·ADR·테스트·운영 문서 | `route/algorithm/**`의 무관한 변경, `ai/**`, `recommendation/**`, `travelplan/**`, Entity·Repository·migration, 정적 자산 |
| S1 추정 일정·추천 | `travelplan`의 계산·음식점 검색 조정 Service·정책·전달 DTO, `recommendation/**`, 대응 테스트 | `place`·`route/algorithm`·`region`의 공개 Service·DTO, `global/exception/**`, estimate·음식점 검색 API의 Controller·DTO와 관련 요구사항·ADR·테스트 문서 | Entity·Repository·migration, `route/client/**`, `route/service/**`, `ai/**`, 완료·공유 API, 정적 자산 |
| T1 외부 경로 통합·완료 일정·API | `travelplan/**`, 대응 테스트, 새 versioned migration | `user`·`place`·`route`·`recommendation` 공개 계약, `global/exception/**`, DB·API·ADR·테스트·DoD 문서 | 외부 Client 내부 구현, `route/algorithm/**`, `ai/**`, 기존 migration, 정적 자산 |
| W1 실제 화면 | `src/main/resources/static/Routy/**`, 직접 대응 UI 테스트 | API 계약 오류가 확인된 경우 문서 변경 제안 | Java 코드, Repository, migration, 설정, `travela-1.0.0/**` |
| Q1 품질·운영 | `global/**`, 운영 관련 테스트, 승인된 `application*.yml`·`application*.properties`·Docker·배포 파일 | `build.gradle`, 각 도메인의 health용 공개 계약, 운영·DoD·준비도 문서 | 도메인 비즈니스 알고리즘, migration, 정적 화면, 원본 템플릿 |

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

### 5.1 작업별 최소 참조 라우팅

아래 표는 구현 작업을 시작할 때 읽을 최소 기준이다. 모든 작업은 이 표와 별개로 루트·대상 경로의 `AGENTS.md`, 로드맵의 해당 ID, 이 문서의 해당 단계 행, 대상 파일과 직접 참조 코드를 읽는다. `공통 오류`는 `docs/04-api-spec.md` 1절에서 해당 상태만, `완료 판정`은 `docs/10-definition-of-done.md`에서 해당 도메인 절만 뜻한다. 문서를 읽는 것은 수정 권한이 아니며, 계약이 실제로 바뀔 때만 6절의 Conditional 규칙으로 문서 수정을 제안한다.

| Task ID | 필수 참조 | 조건부 참조 |
|---|---|---|
| F0-01 | `docs/07` 2절, 현재 build·설정·실행 진입점 | 불일치한 항목의 `docs/09` 관련 절 |
| F0-02 | `docs/03` 13~14절, `docs/08` 2~3·10절, `docs/09` 2·11절 | dependency 선택이 ADR을 요구할 때 `docs/06` |
| F0-03 | F0-02 결정 기록, `docs/03` 13~14절, `docs/09` 2·11절 | 공통 오류나 공개 API가 바뀔 때만 `docs/04` |
| F0-04A | `docs/04` 1절, `docs/08` 2~3절 | 인증 오류 계약 확정 시 `docs/04` 2절, 결정 기록이 필요할 때 `docs/06` |
| F0-04B | F0-04A 결정 기록, `docs/04` 1절, `docs/08` 2~3절 | 운영 로그 계약이 바뀔 때 `docs/09` 4절 |
| F0-05 | `docs/09` 2~4절, `docs/10` 3·13절 | 발견한 문제의 책임 문서 |
| U1-01 | `docs/01` FR-13, `docs/04` 1~2절, `docs/07` 5절, `docs/09` 3·10절 | 새 결정 기록이 필요할 때 `docs/06` |
| U1-02 | `docs/03` 3·8·12~14절, `docs/08` 6.1·6.8절 | User 삭제 계약이 바뀔 때 `docs/03` 12절과 관련 ADR |
| U1-03 | `docs/04` 1절과 `POST /api/users`, `docs/08` 6.1절 | DB 계약이 바뀔 때 `docs/03` 3절 |
| U1-04 | `docs/04` 1절과 `POST /api/auth/login`, `docs/08` 6.1절, `docs/09` 3절 | JWT 결정이 바뀔 때 `docs/06`의 인증 ADR |
| U1-05A | `docs/01` FR-14, `docs/03` 8절, `docs/04` 11절, `docs/08` 6.1·6.8절, `docs/09` 5절 | 카운터 schema·한도 계약이 바뀔 때 해당 `docs/03`·`docs/04` 절 |
| U1-05B | `docs/01` FR-14, `docs/03` 8절, `docs/04` 1절의 중복 요청, `docs/08` 6.1·6.8절, `docs/09` 6절 | 실행 상태 schema·오류 계약이 바뀔 때 해당 `docs/03`·`docs/04` 절 |
| U1-06 | `docs/08` 6.1절, `docs/10` 3~4·13절 | 발견한 문제의 책임 문서 |
| G1-01 | `docs/01` 국내 지역·FR-01, `docs/06` ADR-026·ADR-035, `docs/09` 9절 | 데이터 갱신 운영 절차가 바뀔 때 `docs/09` |
| G1-02~03 | G1-01 결정 기록, `docs/01` FR-01, `docs/08` 5·6.2절 | 공개 필드가 바뀔 때 `docs/04` 3절 |
| G1-04 | `docs/01` FR-01, `docs/04` 1절과 `GET /api/regions`, `docs/08` 6.2절 | 지역 계약 결정이 바뀔 때 관련 ADR |
| G1-05 | `docs/08` 5·6.2절, `docs/10` 3·5절 | 발견한 문제의 책임 문서 |
| A1-01 | `docs/01` FR-02·FR-15, `docs/04` 3~4절, `docs/08` 4·6.3절 | 오류 변환 변경 시 `docs/04` 1절 |
| A1-02 | `docs/04` 지역·메뉴 AI endpoint, `docs/07` 5절의 OpenAI 항목, `docs/09` 3·5·7·10절 | 공식 계약을 반영해 목표 계약이 바뀔 때 `docs/04`·`docs/06` |
| A1-03 | A1-02 결정 기록, `docs/04` 지역·메뉴 AI endpoint, `docs/08` 4·6.3절, `docs/09` 3·7절 | 승인된 계약과 불일치가 발견될 때 A1-02 재검토 |
| A1-04 | `docs/04` 1·3·11절, `docs/09` 5~7절, `docs/08` 6.3절 | 한도 저장소나 지역 허용 목록 공개 계약 변경 시 `user`·`region` 공개 Service·DTO |
| A1-05 | `docs/01` FR-15, `docs/04` 1·4·11절, `docs/08` 4·6.3절, `docs/09` 5~7절 | 한도 저장소 공개 계약 변경 시 `user` 공개 Service·DTO |
| A1-06 | `docs/08` 4·6.3절, `docs/10` 3·6·13절 | 발견한 문제의 책임 문서 |
| R1-01 | `docs/01` FR-06, `docs/06` ADR-004·ADR-029, `docs/08` 6.5절, `docs/07` 5절 | 수치·동률 결정 기록을 추가할 `docs/06` |
| R1-02~06 | R1-01 결정 기록, `docs/01` FR-06·FR-09 관련 규칙, `docs/08` 6.5절 | 결정이 바뀔 때만 관련 ADR |
| R1-06A | R1-01 결정 기록, `docs/01` FR-03·FR-06과 숙소 탐색 규칙, `docs/08` 6.4~6.5절, `docs/10` 7~8절 | 결정이 바뀔 때만 관련 ADR |
| R1-07 | `docs/08` 6.5절, `docs/10` 3절과 8절의 순수 알고리즘 | 측정 결과 기록이 필요할 때 `docs/05` 8절 |
| C1-01~02 | `docs/01` FR-03, `docs/04` 5절, `docs/08` 4·6.4절, `docs/09` 7·9~10절 | 공통 오류 변경 시 `docs/04` 1절 |
| C1-03~04 | `docs/01` FR-07~08, `docs/04` 7·11절, `docs/08` 4·6.7절, `docs/09` 5·7·10절 | 추정 계수 사용 시 R1-01 결정 기록 |
| C1-05 | `docs/09` 5·7·9~10절과 `docs/04`의 카카오 장소·경로 endpoint | 공식 카카오 계약 변경이 확인될 때 `docs/04`·`docs/06` |
| P1-01 | `docs/01` FR-03~05, `docs/04` 5절, `docs/08` 6.4절 | 정책 결정 변경 시 관련 ADR |
| P1-02 | `docs/04` 5·12절, `docs/06` ADR-028, `docs/09` 3·9~10절 | 새 보안 결정 기록을 위한 `docs/06` |
| P1-03 | P1-02 결정 기록, `docs/04` 5·12절, `docs/08` 6.4·7절 | 오류 계약 변경 시 `docs/04` 1절 |
| P1-03A | `docs/01` 국내 지역·FR-01·FR-03, `docs/04` 지역·장소 검색 API, `docs/06` ADR-035, `docs/08` 6.2·6.4절 | `region` 공개 조회 계약 변경이 필요하면 해당 Service·DTO, 지역·필터 정책 자체가 바뀔 때만 `docs/01`·`docs/06` 변경 제안 |
| P1-04~05 | C1-05 감사 기록, `docs/04` 1·5·11~12절, `docs/08` 4·6.4·7절, `docs/09` 3·5·7·9절 | 지역·한도 공개 계약 변경 시 해당 공개 Service·DTO |
| P1-06 | `docs/01` 6절 제작 중 데이터 수명, `docs/04` 5·12절, `docs/08` 7~8절, `Routy/INTEGRATION.md` | 메뉴·식사·일정·음식점 상태는 후속 Task, API 계약 오류 발견 시 문서 변경 제안 |
| P1-07 | `docs/08` 6.4·7~8절, `docs/10` 7·15절 | 발견한 문제의 책임 문서 |
| R2-01~04 | C1-05 감사 기록, `docs/01` FR-07~08·FR-14, `docs/04` 7·11절, `docs/08` 4·6.7절, `docs/09` 5·7절 | 공식 계약 변경 시 C1-05 재검토 후 `docs/04`·`docs/06` |
| R2-05~06 | `docs/01` FR-07~09, `docs/04` 7절, `docs/08` 6.7절, `docs/09` 7절 | 경로 결과 계약 변경 시 `docs/04`·관련 ADR |
| R2-07A | `docs/01` FR-14, `docs/03` 8절, `docs/04` 11절, `docs/08` 6.7절, `docs/09` 5절 | 사용자 한도 공개 계약 변경 시 `user` 공개 Service·DTO |
| R2-07B | R2-07A 계약, `docs/01` FR-08·FR-14, `docs/04` 7절, `docs/08` 6.7절, `docs/09` 4~5·7절 | 집계 metric 구현은 Q1-04로 이관하고 공개 관측 계약이 바뀔 때만 `global` 계약 검토 |
| S1-01~04 | `docs/01` 여행 조건·FR-05~06·FR-09, `docs/04` 6절, `docs/08` 6.5~6.6절 | 새 정책 결정이 필요할 때 `docs/06` |
| S1-05 | `docs/01` 숙소·FR-03, `docs/04` 호텔 검색 endpoint, `docs/08` 6.4·6.6절 | place 공개 계약 변경 시 해당 Service·DTO |
| S1-06 | `docs/01` 식사와 음식점, `docs/04` 음식점 검색 endpoint, `docs/08` 6.6·6.10절 | place·route 공개 계약 변경 시 해당 Service·DTO |
| S1-06A | `docs/02` Spring 계층·place·travelplan·recommendation 책임, `docs/04` 음식점 검색 endpoint, `docs/08` 6.6·6.10절 | Controller 패키지는 place의 travelplan 역참조 없이 단일 조정 Service를 호출하는 의존 방향을 확인한 뒤 Change Envelope에서 확정 |
| S1-07 | `docs/08` 6.6·6.10절, `docs/10` 9·12절 | 발견한 문제의 책임 문서 |
| T1-01 | `docs/01` FR-10~13, `docs/03` 2·5·10·12·15절, `docs/04` 7~10절, `docs/07` 5절 | 새 결정을 기록할 `docs/06` |
| T1-02~03 | T1-01 결정 기록, `docs/03` 2·4~14절, `docs/08` 6.8절 | 공개 DTO가 바뀔 때 `docs/04` 7~10절 |
| T1-04~06 | `docs/01` FR-07~10·FR-14, `docs/02` 7·10절, `docs/04` 7·11~12절, `docs/08` 6.7~6.8절 | 공개 Service·DTO 계약 변경 시 소유 도메인 기준 |
| T1-07~09 | `docs/01` FR-11~12, `docs/03` 10·12절, `docs/04` 8·10절, `docs/08` 6.9절 | 공유·삭제 결정 변경 시 관련 ADR |
| T1-10 | `docs/08` 6.7~6.9·7절, `docs/10` 10~11·15절 | 발견한 문제의 책임 문서 |
| W1-00 | `docs/01`의 목표 사용자 흐름, `docs/08` 8절, `docs/10` 14절, `Routy/AGENTS.md`, `Routy/INTEGRATION.md`의 현재 시안과 목표 흐름 | 정보 구조가 목표 계약과 충돌할 때 해당 책임 문서 변경 제안 |
| W1-01A | W1-00 공통 골격, U1 인증 결과, `docs/04` 회원가입·로그인 API, `docs/08` 6.1절과 8절의 인증 만료 복구, `Routy/INTEGRATION.md`의 인증 흐름 | API 계약 오류 발견 시 문서 변경 제안 |
| W1-01B | W1-00 공통 골격, G1 지역 결과·A1-04 지역 추천 API와 인증 사용자 전달 계약, `docs/04` 지역 직접 검색·AI 추천 API, `docs/08` 8절의 지역 UI 항목, `Routy/INTEGRATION.md`의 지역 흐름 | API 계약 오류 발견 시 문서 변경 제안 |
| W1-02 | W1-00 공통 골격, P1-03A 서버 계약·P1-06 상태 모듈, `docs/01` FR-03~05, `docs/04` 장소 검색 API·12절, `docs/08` 6.4·8절, `docs/10` 7절, `Routy/INTEGRATION.md` | API 계약 오류 발견 시 문서 변경 제안 |
| W1-02A | A1-05 메뉴 분석 API·W1-02 관광지 선택 상태, `docs/04` 메뉴 분석 API, `docs/08` 6.3·8절, `Routy/INTEGRATION.md`의 메뉴 분석 흐름 | API 계약 오류 발견 시 문서 변경 제안 |
| W1-03 | W1-00 공통 골격, `docs/01` 여행 조건·FR-09, `docs/04` estimate endpoint, `docs/08` 6.6·8절, `Routy/INTEGRATION.md` | API 계약 오류 발견 시 문서 변경 제안 |
| W1-03A | W1-00 공통 골격, `docs/01` 식사와 음식점, `docs/04` 음식점 검색 endpoint, `docs/08` 6.6·6.10·8절, `Routy/INTEGRATION.md` | API 계약 오류 발견 시 문서 변경 제안 |
| W1-04~05 | W1-00 공통 골격, `docs/04` 생성·조회·편집·삭제·공유 endpoint, `docs/08` 8절, `Routy/INTEGRATION.md` | API 계약 오류 발견 시 문서 변경 제안 |
| W1-06 | `docs/01` 관련 목표 계약, `docs/08` 8절, `docs/10` 14~15절, `Routy/INTEGRATION.md`의 현재 시안과 남은 검증 | 발견한 문제의 책임 문서 |
| Q1-01~02 | `docs/09` 2~4·9절, `docs/10` 13절 | 발견한 계약 불일치의 책임 문서 |
| Q1-03~04 | `docs/09` 4·8절, `docs/10` 13절 | dependency가 필요할 때 `build.gradle`과 관련 ADR |
| Q1-05~06 | `docs/03` 13절, `docs/08` 10절, `docs/09` 2·10~12절 | 공식 제공자 계약 변경 시 `docs/04`·`docs/06` |
| Q1-07 | `docs/07` 4~8절, `docs/10` 전체 중 실제 구현 범위 | 확인된 상태만 `docs/07`에 갱신 |

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
- `application*.yml`, `application*.properties`: 승인된 profile·외부 연결·로그 설정이 필요한 경우. 같은 profile과 위치에서 동일 key를 두 형식에 중복 정의하지 않으며, 형식 통합이나 파일 제거는 대상·영향을 별도로 설명하고 승인받는다.

한 작업에서 관련 없는 문서와 테스트를 정리하지 않는다. `AGENTS.md`와 이 경계표의 변경은 D0의 별도 작업으로 다룬다.

구현 Task를 완료 처리하면서 `docs/07-implementation-readiness.md`의 실제 상태 또는 `docs/11-command-roadmap.md`의 다음 시작 작업을 바꾸는 경우에는 두 파일을 Change Envelope의 Conditional Paths에 파일별 조건과 이유를 적어 포함한다. 검증 증거 없이 상태만 앞당기는 변경은 허용하지 않는다.

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
9. 완료 처리하는 작업은 `docs/07`의 실제 상태와 `docs/11` 16절의 다음 시작 작업이 같은 검증 결과를 가리키는지 확인한다.
10. 완료 보고에 실제 변경 경로, 승인된 Conditional 사용 여부와 실행하지 못한 검증을 남긴다.

문서 전용 작업은 Gradle 테스트를 생략할 수 있지만 이유와 미검증 범위를 기록한다. 검사 목적으로 사용자 변경을 stash, reset, checkout하거나 삭제하지 않는다.

## 10. 외부 데이터와 비밀값 경계

- 카카오 좌표·주소·전화번호·카테고리·장소명·원문과 경로 원문을 DB, cache, 서버 session, 로그와 fixture에 저장하지 않는다.
- 작성 중 좌표의 브라우저 메모리 사용은 K0 허용 조건 안에서만 가능하며 localStorage·sessionStorage·IndexedDB에 저장하지 않는다.
- 중복 요청 저장소에는 사용자와 `requestId`의 식별 정보 및 상태만 10분간 두고 결과 리소스 ID·payload·response를 저장하지 않는다. 처리 중·성공 중복은 계약된 409로 차단하고 외부 호출·저장·호출량 차감을 반복하지 않는다.
- API 키, JWT secret, 비밀번호와 Authorization 헤더를 코드·Git·로그·오류 응답에 남기지 않는다.
- 자동 테스트에서 실제 OpenAI·카카오 API를 호출하지 않는다.
- 완료·공유 조회는 외부 API를 호출하지 않는다.
