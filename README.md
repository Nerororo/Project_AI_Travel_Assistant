# ✈️ Travel

> **사용자의 여행 취향과 원하는 장소를 바탕으로 여행지를 추천하고,
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

**Travel**은 사용자가 입력한 여행 지역, 취향, 방문하고 싶은 장소, 먹고 싶은 음식을 기반으로
여행지를 추천하고, 선택된 장소들의 위치를 고려하여 **하나의 여행 일정으로 구성해주는 서비스**입니다.

예를 들어,

```text
부산 2박 3일 여행

✓ 바다와 사진 찍는 장소를 좋아함
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

## ✨ 주요 기능

* [ ] 🎯 여행 취향 기반 여행지 추천
* [ ] 📍 방문 희망 장소 기반 주변 여행지 추천
* [ ] 🍽️ 먹고 싶은 음식 기반 맛집 추천
* [ ] 🗺️ 여행지 간 이동 동선 구성
* [ ] 🏨 방문 장소를 고려한 숙소 추천
* [ ] 📅 날짜별 여행 일정 생성
* [ ] 💾 여행 계획 저장 및 조회
* [ ] 🔐 회원가입 및 로그인

---

## 🚀 Development Progress

* [x] Spring Boot 프로젝트 구성
* [x] Java 21 개발 환경 구성
* [ ] MySQL/JPA 런타임 연결 검증
* [x] Docker Compose 구성
* [ ] JPA 의존성 및 스키마 초기화 적용
* [x] 환경변수 분리
* [x] Git / GitHub 연동
* [ ] 장소 관리 기능
* [ ] 여행 계획 관리 기능
* [ ] AI 여행지 추천
* [ ] 이동 경로 계산
* [ ] 호텔 추천
* [ ] 맛집 추천
* [ ] 날짜별 일정 생성
* [ ] 회원 / 인증
* [ ] 테스트
* [ ] 배포

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
└── 11-command-roadmap.md
```

처음 구현을 시작할 때는 큰 Phase 대신 [`docs/11-command-roadmap.md`](./docs/11-command-roadmap.md)의 작업 ID를 한 개씩 진행합니다.

---

## 📌 Project Status

> 현재 개발 진행 중인 개인 프로젝트입니다.

구현이 완료된 기능은 위 체크리스트에 지속적으로 반영합니다.
