# ✈️ Routy

> **사용자가 선택한 국가 또는 도시를 바탕으로 방문 장소를 추천하고,
> 이동 동선과 숙소·맛집까지 함께 고려해 여행 계획을 만들어주는 서비스입니다.**

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/MySQL-8.4-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
  <img src="https://img.shields.io/badge/OpenAI-API-412991?style=for-the-badge&logo=openai&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
</p>

---

## 🌍 프로젝트 소개

여행을 계획할 때는 단순히 관광지를 찾는 것뿐 아니라,

* 어디를 방문할지
* 어떤 순서로 이동할지
* 어느 지역에 숙소를 잡을지
* 여행 중 어디에서 식사할지

까지 함께 고려해야 합니다.

**Routy**는 사용자가 국가 또는 도시를 직접 선택할 수 있고, 국가만 선택한 경우에는 AI가 도시 후보를 제안합니다. 선택된 도시 안에서 Google Places로 방문 장소를 찾고, 선택된 장소들의 위치를 고려하여 **하나의 여행 일정으로 구성해주는 서비스**입니다.

예를 들어,

```text
부산 2박 3일 여행

✓ 부산을 여행 도시로 선택
✓ 해운대와 감천문화마을은 꼭 방문
✓ 돼지국밥과 회를 먹고 싶음
```

와 같이 입력하면,

```text
여행지 추천
    ↓
방문 장소 선정
    ↓
이동 동선 구성
    ↓
숙소 추천
    ↓
동선 주변 맛집 추천
    ↓
2박 3일 여행 일정 생성
```

의 형태로 여행 계획을 제공합니다.

---

## 🧭 설계 원칙

Routy는 AI가 현재 도시 후보 추천을 보조하고, 서버가 검증 가능한 여행 규칙을 결정하도록 책임을 분리합니다. 음식 자연어 해석은 후속 범위입니다.

* AI는 국가 기반 도시 후보와 추천 이유를 구조화된 데이터로 제안합니다.
* 서버는 Google Places로 국가·도시·장소를 검증하고 Google Place ID를 기준으로 참조합니다.
* 거리 계산, 경로 최적화, 호텔·음식점 점수, 날짜별 일정 배치는 결정적인 백엔드 로직으로 처리합니다.
* AI 응답과 외부 제공자 응답은 검증한 뒤에만 사용하며, AI가 Place ID·최종 경로·최종 순위를 직접 결정하지 않습니다.

---

## ✨ 주요 기능

* [ ] 🌍 국가 기반 AI 도시 후보 추천
* [ ] 📍 Google Places 기반 방문 장소 추천
* [ ] 🍽️ 점심·저녁 시간대와 동선을 고려한 맛집 추천
* [ ] 🗺️ 실제 이동 시간과 장소별 체류 시간을 고려한 동선 구성
* [ ] 🏨 방문 장소를 고려한 숙소 추천
* [ ] 📅 날짜별 여행 일정 생성
* [ ] ✅ 도시·관광지·호텔의 Google Place ID·유형·도시 소속 검증
* [ ] ⏱️ 장소 유형별 체류 시간과 점심·저녁 식사 슬롯 반영
* [ ] 🔁 Nearest Neighbor 기반 방문 순서 최적화와 정적 이동 시간 행렬 적용
* [ ] 🧾 저장된 일정 기반 음식점 후보 검색과 시간표 불변성 보장
* [ ] 💾 여행 계획 저장 및 조회
* [ ] 🔐 회원가입 및 로그인

---

## 🚀 Development Progress

* [x] Spring Boot 프로젝트 구성
* [x] Java 21 개발 환경 구성
* [x] `GET /hello` 개발 확인 endpoint 구성
* [x] Docker Compose 구성
* [x] 환경변수 분리
* [x] Git / GitHub 연동
* [x] 요구사항·아키텍처·DB·API·테스트·운영·완료 기준 문서화
* [x] UI 검토용 Travela 원본과 Routy 정적 파일 배치
* [ ] 기존 템플릿을 사용하지 않는 Routy 전용 UI 설계
* [ ] JPA 의존성 및 스키마 초기화 적용
* [ ] DB 자동 테스트 환경과 Flyway migration 기반 구성
* [ ] MySQL/JPA 런타임 연결 검증
* [ ] Google 장소 검색·참조 관리 기능
* [ ] Place 참조 Entity·Repository·Google Place ID UNIQUE 제약 구현
* [ ] GooglePlacesClient·fake·DTO와 PlaceService 공개 계약 구현
* [ ] 공통 오류 응답·Global Exception Handler 및 도시 직접 선택·장소 상세 API 구현
* [ ] 실제 Google Places client의 Field Mask·timeout·오류 변환 구현
* [ ] 이동 경로 계산
* [ ] Haversine 거리 계산·Nearest Neighbor 최적화·경로 API 구현
* [ ] Google Routes client·fake와 정적 이동 시간 행렬 구현
* [ ] 여행 계획 관리 기능
* [ ] TravelPlan Aggregate·내부 저장·조회·교체·삭제 계층 구현
* [ ] 공유 Place 참조 재사용과 계획 삭제 후 Place 유지 무결성 검증
* [ ] AI 도시 후보 추천
* [ ] AI 구조화 응답 DTO·fake·검증 Service 구현
* [ ] OpenAI client와 국가 기반 도시 후보 API 구현
* [ ] 방문 장소 추천
* [ ] 호텔 추천
* [ ] 맛집 추천
* [ ] 날짜별 일정 생성
* [ ] 체류 시간 정책·일일 시간 용량·날짜별 일정 배치 구현
* [ ] TravelPlan 입력의 Google Place ID 무결성 검증과 최종 생성 API 구현
* [ ] TravelPlan 공개 조회·수정·삭제와 저장 일정 기반 음식점 검색 API 구현
* [ ] 최종 일정 통합 회귀·MVP 완료 기준 점검
* [ ] 공통 오류 처리·테스트·운영 설정
* [ ] local/test/prod profile·Flyway 운영 설정과 health check 구현
* [ ] 배포
* [ ] 회원 / 인증
* [ ] 회원·JWT 인증과 TravelPlan 소유권 적용
* [ ] 새 Routy 프론트 구현과 API 단계별 연결
* [ ] 전체 브라우저 흐름·반응형·접근성 검증

세부 작업 순서와 완료 판정은 [`docs/11-command-roadmap.md`](./docs/11-command-roadmap.md)를 따릅니다.

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
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
</p>

### AI

<p>
  <img src="https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white">
</p>

### Tools

<p>
  <img src="https://img.shields.io/badge/IntelliJ_IDEA-000000?style=flat-square&logo=intellijidea&logoColor=white">
  <img src="https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white">
</p>

### Frontend (계획)

Spring Boot의 `src/main/resources/static/Routy/**`에서 별도 프론트 빌드 도구 없이 동작하는 Vanilla HTML·CSS·JavaScript UI를 새로 설계할 예정입니다. 현재 배치된 Travela 파일은 완성 화면이 아니며, 원본 `travela-1.0.0/**`는 보존합니다.

프론트 구현은 백엔드 API 준비 시점에 맞춰 S1 단계에서 연결합니다. S1-01에서 참고 이미지, 화면 구조, 디자인 시스템, 반응형·접근성, 상태 유지 방식을 먼저 확정하고 S1-02~S1-07에서 기능별로 구현합니다.

---

## 🧱 아키텍처와 책임 경계

도메인 중심 패키지 구조를 사용하며, Controller·Service·Repository·DTO·Entity의 책임을 분리합니다.

```text
API Controller
      ↓
Application Service
      ├── Place Service      : Google Places 검증·Place 참조 관리
      ├── Route Service      : 거리·이동 시간·방문 순서 계산
      ├── Recommendation     : 장소·호텔·음식점 후보 평가
      ├── AI Service         : 도시 후보 생성과 응답 검증
      └── TravelPlan Service : 일정 생성·저장·조회·수정·삭제 조합
      ↓
Repository / External Client
```

외부 통신은 `ai`, `place/client`, `route/client`에 격리합니다. 경로 알고리즘과 추천 정책은 Spring·JPA·HTTP·AI에 직접 의존하지 않는 순수 Java 로직으로 유지합니다.

일정 생성 전에는 도시·관광지·호텔의 Google Place ID와 역할·도시 소속을 모두 검증합니다. 외부 검증과 일정 계산이 성공한 뒤 하나의 트랜잭션으로 전체 TravelPlan을 저장해 부분 저장을 방지합니다.

---

## 📚 Documents

상세 요구사항과 설계 문서는 [`docs/`](./docs)에서 관리합니다.

```text
docs/
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

처음 구현을 시작할 때는 큰 Phase 대신 [`docs/11-command-roadmap.md`](./docs/11-command-roadmap.md)의 작업 ID를 한 개씩 진행합니다.

화면 작업은 백엔드가 모두 끝난 뒤 한 번에 붙이지 않습니다. F0 이후 S1-01에서 새 UI를 설계하고, 도시 추천·관광지와 호텔·일정·음식점·인증 API가 준비될 때마다 대응하는 S1 작업에서 순차적으로 연결합니다.

문서별 기준은 분리합니다. 요구사항은 `01`, 아키텍처는 `02`, DB 계약은 `03`, HTTP 계약은 `04`, 설계 결정은 `06`, 테스트는 `08`, 운영은 `09`, 완료 기준은 `10` 문서를 기준으로 합니다.

---

## 🧪 개발·운영 기준

* 자동 테스트는 실제 OpenAI·Google API를 호출하지 않고 fake 또는 mock을 사용합니다.
* API 키, DB 비밀번호, access token, 사용자 원문과 외부 제공자 원문 요청·응답은 코드·Git·로그·오류 응답에 기록하지 않습니다.
* DB 스키마 변경은 Flyway versioned migration으로 관리하며, Hibernate는 `ddl-auto: validate`만 사용합니다.
* 로컬 기본 실행과 자동 테스트는 외부 API를 호출하지 않습니다. 실제 연동은 키를 별도로 주입한 수동 smoke 환경에서만 확인합니다.

---

## 📌 Project Status

> 현재 개발 진행 중인 개인 프로젝트입니다.

구현이 완료된 기능은 위 체크리스트에 지속적으로 반영합니다.
