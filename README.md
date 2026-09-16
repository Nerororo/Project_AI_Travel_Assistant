# ✈️ Routy

> **국내 여행 지역과 관광지를 선택하면 체류시간과 이동시간을 반영해
> 자동차 또는 대중교통 여행 일정을 만들어주는 서비스입니다.**

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/MySQL-8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
  <img src="https://img.shields.io/badge/OpenAI-API-412991?style=for-the-badge&logo=openai&logoColor=white">
  <img src="https://img.shields.io/badge/Kakao-API-FFCD00?style=for-the-badge&logo=kakao&logoColor=000000">
</p>

---

## 🌍 프로젝트 소개

여행을 계획할 때는 단순히 관광지를 찾는 것뿐 아니라,

* 어디를 방문할지
* 어떤 순서로 이동할지
* 각 장소에 얼마나 머무를지
* 어느 지역에서 숙박하고 식사할지
* 자동차와 대중교통 중 무엇으로 이동할지

까지 함께 고려해야 합니다.

**Routy**는 서울특별시·광역시·세종특별자치시 또는 도·특별자치도 아래 시·군 하나를 여행 지역으로 선택하고, 카카오에서 찾은 관광지·숙소·음식점을 사용자가 직접 고르면 **체류시간과 이동시간을 반영한 하나의 여행 일정으로 구성해주는 서비스**입니다. 지역을 직접 선택하는 대신 AI에게 여행 성향에 맞는 국내 지역 후보 3개와 이유를 추천받을 수도 있습니다.

예를 들어,

```text
부산광역시 2박 3일 여행

✓ 자동차로 이동
✓ 해운대구를 관광지 검색 필터로 선택
✓ 해운대와 청사포는 꼭 방문
✓ 돼지국밥과 회를 먹고 싶음
```

와 같이 선택하고 입력하면,

```text
국내 여행 지역 선택 또는 AI 지역 추천
    ↓
관광지 선택과 체류시간 조정
    ↓
숙소 지도 탐색과 직접 선택
    ↓
AI 메뉴 후보 확정
    ↓
Haversine 기반 일정 추정
    ↓
음식점 지도 탐색과 직접 선택
    ↓
최종 인접 구간 실제 경로 검증
    ↓
완료 일정 저장
```

의 형태로 여행 계획을 제공합니다.

---

## 🧭 설계 원칙

Routy는 AI와 Spring Backend의 책임을 분리합니다.

* AI는 허용된 국내 지역 목록에서 후보 3개와 이유를 만들고, 음식 자연어를 메뉴·검색어·이유·기준 관광지 후보로 구조화합니다.
* 서버는 인증·인가, 장소 검증, 거리·순서·시간 계산, 음식점 추천 점수, 저장과 외부 장애 처리를 담당합니다.
* AI는 장소의 존재, 방문 순서, 시간표, 추천 점수와 저장 성공을 결정하지 않습니다.
* Haversine·Nearest Neighbor·2-opt로 먼저 계산하고, 최종 후보의 인접 구간만 실제 경로 API로 검증합니다.
* 외부 호출과 계산 중에는 DB 트랜잭션을 열지 않고, 모든 검증이 끝난 뒤 짧은 트랜잭션으로 완료 일정 전체를 저장합니다.

---

## ✨ 주요 기능

* [ ] 🌏 국내 최종 여행 지역 직접 선택과 AI 지역 후보 3개 추천
* [ ] 📍 카카오 기반 관광지 검색·검증과 사용자 표시 이름 입력
* [ ] ⏱️ 장소별 기본 체류시간 계산과 10분 단위 조정
* [ ] 🔁 Haversine·Nearest Neighbor·2-opt 기반 추정 동선 구성
* [ ] 🚗 자동차 또는 대중교통 실제 경로 검증
* [ ] 🏨 기하 중앙값·메도이드·현재 지도 영역 기반 숙소 탐색
* [ ] 🍽️ AI 메뉴 구조화와 동선 이탈이 작은 음식점 탐색
* [ ] 📅 1~7일 일정과 점심·저녁 시간대 배치
* [ ] 💾 완료 일정 저장·조회·제한 편집·삭제
* [ ] 🔗 외부 API 호출 없는 완료 일정 공유
* [ ] 🔐 회원가입·JWT 인증·일정 소유권
* [ ] 🚦 사용자별 AI·장소·경로 호출 한도와 fallback

---

## 🚀 Development Progress

* [x] Spring Boot 프로젝트 구성
* [x] Java 21 개발 환경 구성
* [x] `GET /hello` 개발 확인 endpoint 구성
* [x] Docker Compose 구성
* [x] 환경변수 분리
* [x] Git / GitHub 연동
* [x] 요구사항·아키텍처·DB·API·테스트·운영·완료 기준 문서화
* [x] 국내·카카오 기반 정책과 도메인 경계 정리
* [x] 카카오 좌표의 일시 사용·즉시 폐기 정책 확인과 문서 반영
* [x] UI 검토용 Travela 원본과 Routy 정적 파일 배치
* [x] Spring Data JPA·MySQL·Flyway 개발 기반 구성과 MySQL 8.4 migration 검증
* [x] 회원가입·JWT 로그인·공개 API 경계와 보호 API 인증 구현
* [x] 사용자·서비스 호출 한도와 10분 `requestId` 중복 요청 상태 구현
* [ ] 국내 행정구역 기준 데이터와 검색 구현
* [ ] Haversine·Nearest Neighbor·2-opt 순수 Java 알고리즘 구현
* [ ] OpenAI·카카오 장소·자동차·대중교통 Client 계약과 Fake 구현
* [ ] 실제 카카오 장소 검색과 일회성 `selectionToken` 구현
* [ ] 자동차·대중교통 경로 검증과 호출 한도 fallback 구현
* [ ] 추정 일정·숙소 탐색·음식점 추천 구현
* [ ] 완료 일정 Aggregate와 저장·조회·편집·삭제·공유 API 구현
* [x] Routy 전용 app shell과 회원가입·로그인 UI API 연동
* [ ] 전체 자동 테스트·브라우저 흐름·운영·배포 검증

세부 작업 순서와 완료 판정은 [`docs/11-command-roadmap.md`](./docs/11-command-roadmap.md)를 따릅니다. 현재 U1 인증·호출 한도 기반과 W1-01A 인증 화면 연결까지 구현·검증됐으며, 지역·장소·일정 도메인과 실제 외부 API 연결은 후속 작업입니다.

---

## 🛠 Tech Stack

### Backend

<p>
  <img src="https://img.shields.io/badge/Java_21-007396?style=flat-square&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring_Boot_4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white">
  <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white">
</p>

### Database & Infrastructure

<p>
  <img src="https://img.shields.io/badge/MySQL_8.4-4479A1?style=flat-square&logo=mysql&logoColor=white">
  <img src="https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
</p>

### AI & External API

<p>
  <img src="https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white">
  <img src="https://img.shields.io/badge/Kakao_Local-FFCD00?style=flat-square&logo=kakao&logoColor=000000">
  <img src="https://img.shields.io/badge/Kakao_Mobility-FFCD00?style=flat-square&logo=kakao&logoColor=000000">
  <img src="https://img.shields.io/badge/Kakao_Map-FFCD00?style=flat-square&logo=kakao&logoColor=000000">
</p>

### Tools

<p>
  <img src="https://img.shields.io/badge/IntelliJ_IDEA-000000?style=flat-square&logo=intellijidea&logoColor=white">
  <img src="https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white">
</p>

Spring Data JPA·MySQL·Flyway·Spring Security·JWT는 구현·검증됐습니다. OpenAI·카카오 장소·경로 Client는 목표 기술이며 아직 구현하지 않았습니다.

### Frontend (계획)

Spring Boot의 `src/main/resources/static/Routy/**`에서 별도 프론트 빌드 도구 없이 동작하는 Vanilla HTML·CSS·JavaScript UI를 구현했습니다. Landing, Auth, Journey Workspace, My Trips, Trip Detail, Shared Trip의 화면 골격과 8단계 Workspace를 만들었고, 회원가입·로그인 API와 메모리 인증 상태를 연결했습니다. `travela-1.0.0/**`은 참고용 원본 템플릿으로 보존하고 실제 서비스 화면에는 `Routy/**`만 사용합니다.

JWT는 현재 탭 JavaScript 메모리에만 보관하고 로그아웃·만료·새로고침·탭 종료 시 폐기합니다. 작성 중 카카오 좌표는 후속 장소 기능에서 브라우저 JavaScript 메모리와 해당 서버 요청에서만 일시적으로 사용하고, 완료·취소·새로고침·탭 종료와 요청 처리가 끝나면 즉시 폐기할 예정입니다.

---

## 🧱 아키텍처와 책임 경계

도메인 중심 패키지 구조를 사용하며 Controller·Service·Repository·DTO·Entity의 책임을 분리합니다.

```text
API Controller
      ↓
Application Service
      ├── User Service           : 인증·소유권·사용자별 호출 한도
      ├── Region Service         : 국내 행정구역 기준과 검색
      ├── Place Service          : 카카오 장소 검색·검증·선택
      ├── Route Service          : 거리·방문 순서·실제 경로 검증
      ├── Recommendation Service : 음식점 후보 점수와 정렬
      ├── AI Service             : 지역 후보와 메뉴 구조화
      └── TravelPlan Service     : 일정 계산·저장·조회·편집·공유
      ↓
Repository / External Client
```

외부 HTTP 구현은 OpenAI=`ai/client`, 카카오 장소=`place/client`, 경로=`route/client`에 격리합니다. `route/algorithm`은 Spring·JPA·HTTP·AI에 의존하지 않는 순수 Java로 유지합니다. 다른 도메인은 공개 Service와 전달 DTO로만 사용합니다.

---

## 🔒 데이터와 보안

* 영속 저장하는 장소 정보는 카카오 장소 ID·URL, 사용자가 작성한 표시 이름·메모, 확정 체류시간과 자체 일정 정보입니다.
* 카카오 장소명·좌표·주소·전화번호·카테고리, 검색·경로 원문과 AI 원문은 저장하지 않습니다.
* 좌표는 한 번의 일정 제작 흐름과 서버 요청에서만 일시적으로 사용하고 즉시 폐기합니다.
* 호출 카운터와 `requestId` 처리 상태는 MySQL 공유 저장소에 두되 좌표·payload·response는 저장하지 않습니다.
* 자동 테스트는 실제 OpenAI·카카오 API를 호출하지 않으며 Fake Client를 사용합니다.
* 비밀값과 개인정보는 코드·Git·fixture·로그·오류 응답에 남기지 않습니다.

---

## 📚 Documents

상세 요구사항과 설계 문서는 [`docs/`](./docs)에서 관리합니다.

```text
docs/
├── 00-docs-index.md
├── 01-requirements.md
├── 02-architecture.md
├── 03-database.md
├── 04-api-spec.md
├── 05-development-plan.md
├── 06-decisions.md
├── 07-implementation-readiness.md
├── 08-test-strategy.md
├── 09-operations.md
├── 10-definition-of-done.md
├── 11-command-roadmap.md
└── 12-harness-boundaries.md
```

문서 역할과 권장 읽기 순서는 [`docs/00-docs-index.md`](./docs/00-docs-index.md), 실제 구현 상태는 [`docs/07-implementation-readiness.md`](./docs/07-implementation-readiness.md), 작업 순서와 완료 판정은 [`docs/11-command-roadmap.md`](./docs/11-command-roadmap.md)를 기준으로 합니다.

---

## 🧪 개발·운영 기준

* Controller는 HTTP Request·Response, DTO validation과 Service 호출만 담당합니다.
* DB 스키마 변경은 새 Flyway versioned migration으로 관리하고 적용된 migration은 수정하지 않습니다.
* 로컬 기본 실행과 자동 테스트에서는 실제 외부 API를 호출하지 않습니다.
* 실제 연동은 키·호출 한도·비용을 통제한 별도 smoke 환경에서만 확인합니다.
* 완료 표시는 [`docs/10-definition-of-done.md`](./docs/10-definition-of-done.md)를 확인한 뒤에만 합니다.

---

## 📌 Project Status

> 인증·호출 한도 기반과 인증 화면을 구현·검증한 개인 학습 프로젝트입니다. 지역·장소·일정 기능과 외부 API 연결을 단계적으로 이어가고 있습니다.

구현이 완료된 기능은 위 체크리스트와 구현 준비도 문서에 지속적으로 반영합니다.
