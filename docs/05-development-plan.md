# 개발 계획

## 1. 개발 전략

이 프로젝트는 Spring Boot를 학습하면서 실제 취업 포트폴리오 수준의 서비스를 완성하는 것을 목표로 한다.

따라서 한 번에 전체 기능을 구현하지 않고 다음 순서를 따른다.

```text
작은 기능 설계
 ↓
직접 구현
 ↓
테스트
 ↓
동작 원리 학습
 ↓
Git Commit
 ↓
다음 기능
```

Codex는 구현을 보조하지만, 새 Spring 개념을 사용할 때 개발자가 이해할 수 있도록 설명한다.

큰 Phase를 실제 구현할 때는 `docs/11-command-roadmap.md`의 작은 작업 단위로 나누어 진행한다.

---

# Phase 0 - 현재 개발 환경 확정

## 현재 상태

- [x] Java 21
- [x] Spring Boot 프로젝트 생성
- [x] Gradle 사용
- [x] Spring Boot 애플리케이션 실행
- [x] `GET /hello` 확인
- [x] MySQL 8.4 Docker Compose 구성
- [x] JPA 설정
- [x] `.env` 사용
- [x] `.env` `.gitignore` 등록
- [x] Git / GitHub 기본 연결
- [ ] 프로젝트 문서 적용
- [ ] Root `AGENTS.md` 적용

## 완료 기준

새로운 기능 개발을 시작할 수 있는 로컬 환경이 재현 가능해야 한다.

---

# Phase 1 - Place CRUD

## 학습 목표

- Entity
- `@Id`
- `@GeneratedValue`
- Repository
- Service
- Controller
- DTO
- HTTP Method
- JPA 기본 CRUD
- Validation

## 구현

- [ ] `Place`
- [ ] `PlaceType`
- [ ] Place Request DTO
- [ ] Place Response DTO
- [ ] `PlaceRepository`
- [ ] `PlaceService`
- [ ] `PlaceController`
- [ ] Place CRUD
- [ ] Place Not Found 예외
- [ ] 테스트

## API

```text
POST   /api/places
GET    /api/places
GET    /api/places/{id}
PUT    /api/places/{id}
DELETE /api/places/{id}
```

## 완료 기준

Postman / Swagger 또는 HTTP Client를 통해 Place CRUD가 DB와 정상 연동되고, Service 단위 테스트와 Controller API 테스트가 통과한다.

---

# Phase 2 - TravelPlan 기본 도메인

## 학습 목표

- JPA 연관관계
- 1:N / N:1
- Aggregate
- Transaction
- Lazy Loading
- Cascade
- DTO Mapping

## 구현

- [ ] `TravelPlan`
- [ ] `TravelPlanDay`
- [ ] `TravelPlanPlace`
- [ ] `FoodPreference`
- [ ] `TravelPreference`
- [ ] 여행 계획 CRUD
- [ ] 날짜 유효성 Validation
- [ ] 필수 장소 표시
- [ ] 테스트

## 완료 기준

AI 없이도 사용자가 선택한 장소들로 기본 여행 계획을 저장하고 조회할 수 있으며, 날짜·필수 장소·방문 순서 제약의 통합 테스트가 통과한다.

---

# Phase 3 - OpenAI 연동과 선호 분석

## 학습 목표

- 외부 API Client 분리
- 환경변수
- Configuration
- JSON 직렬화 / 역직렬화
- 구조화된 AI Response
- 외부 API 실패 처리

## 구현

- [ ] OpenAI API Key 환경변수 연결
- [ ] `OpenAiClient`
- [ ] `AiService`
- [ ] 여행 선호 Prompt
- [ ] 구조화된 Preference DTO
- [ ] Parsing Error Handling
- [ ] API 호출 실패 처리
- [ ] 테스트 가능한 Client 구조

## API

```text
POST /api/ai/preferences
```

## 완료 기준

자연어 여행 요청이 안정적으로 Java DTO로 변환된다. fake `AiClient` 기반 성공·계약 위반·제공자 장애 테스트가 통과하고, 실제 OpenAI 호출은 자동 테스트에서 수행하지 않는다.

---

# Phase 4 - 여행지 추천

## 학습 목표

- Service 책임 분리
- 추천 규칙
- DB Query
- AI 후보 + 서버 데이터 조합

## 구현

- [ ] 지역 필터
- [ ] 장소 타입 필터
- [ ] 관심사 기반 후보
- [ ] 필수 장소 보존
- [ ] 추천 결과 DTO
- [ ] 추천 이유
- [ ] 테스트

## 완료 기준

사용자의 구조화된 선호를 이용해 DB 장소 후보를 추천할 수 있다.

---

# Phase 5 - 거리 계산

## 학습 목표

- 순수 Java Domain Logic
- 좌표
- 수학 함수
- 단위 테스트
- 알고리즘과 Spring 분리

## 구현

- [ ] `Coordinate`
- [ ] Distance Calculator
- [ ] 두 장소 사이 거리 계산
- [ ] Distance Matrix 생성
- [ ] 경계값 테스트

## 완료 기준

임의의 Place 목록을 입력하면 모든 장소 간 거리를 계산할 수 있다.

---

# Phase 6 - 여행 경로 최적화

## 학습 목표

- Greedy Algorithm
- Nearest Neighbor
- 시간 복잡도
- 알고리즘 테스트
- 성능 / 품질 비교

## 구현

- [ ] `RouteOptimizer` 인터페이스 또는 단순 구현체
- [ ] `NearestNeighborRouteOptimizer`
- [ ] 총 이동 거리 계산
- [ ] 시작 장소 지정
- [ ] 중복 장소 검증
- [ ] 테스트

## API

```text
POST /api/routes/optimize
```

## 완료 기준

입력 장소 N개에 대해 방문 순서와 총 이동 거리를 반환한다.

---

# Phase 7 - 호텔 추천

## 학습 목표

- 추천 Score 설계
- Comparator / 정렬
- 거리 알고리즘 재사용

## 구현

- [ ] 지역 내 HOTEL 후보 조회
- [ ] 선택 관광지와 호텔 거리 계산
- [ ] Total Distance Score
- [ ] Ranking
- [ ] Top N 반환
- [ ] 테스트

## 완료 기준

같은 여행지 집합에 대해 어떤 호텔이 이동 측면에서 더 유리한지 설명 가능한 점수로 비교할 수 있다.

---

# Phase 8 - 음식점 추천

## 학습 목표

- 복합 추천 조건
- 점수 정규화 기초
- 여행 동선과 추천 결합

## 구현

- [ ] 음식명 / 카테고리 후보 조회
- [ ] 경로와 음식점 간 이탈 거리 계산
- [ ] 음식 일치 여부
- [ ] 추천 점수
- [ ] Ranking
- [ ] 테스트

## 완료 기준

사용자가 원하는 음식에 해당하면서 여행 동선을 크게 벗어나지 않는 음식점을 추천한다.

---

# Phase 9 - 날짜별 일정 생성

## 학습 목표

- 여러 알고리즘 / Service 조합
- Orchestration Service
- Transaction
- 일정 도메인 설계

## 구현

- [ ] 여행 일수 계산
- [ ] 장소 날짜별 분배
- [ ] 날짜별 경로 최적화
- [ ] 음식점 삽입
- [ ] 호텔 시작 / 종료 고려
- [ ] 전체 TravelPlan 저장
- [ ] 통합 테스트

## 완료 기준

여행 기간과 장소 목록을 입력하면 Day별 일정이 만들어지고 DB에 저장된다.

---

# Phase 10 - 2-opt 경로 개선

## 학습 목표

- 휴리스틱 개선
- Before / After 측정
- 알고리즘 품질 검증

## 구현

- [ ] 2-opt
- [ ] Nearest Neighbor 결과와 비교
- [ ] 개선 거리 / 개선율 측정
- [ ] 테스트 데이터 여러 개 비교

## 완료 기준

특정 경로에서 Nearest Neighbor보다 이동 거리가 줄어드는 것을 테스트 및 기록으로 증명한다.

이 결과는 포트폴리오 / 자소서에 사용할 수 있도록 남긴다.

---

# Phase 11 - Security / User

## 학습 목표

- Spring Security
- Authentication
- Authorization
- Password Encoder
- JWT

## 구현

- [ ] User
- [ ] 회원가입
- [ ] Password Hash
- [ ] 로그인
- [ ] JWT
- [ ] TravelPlan 소유권 검증
- [ ] Security Test

## 완료 기준

사용자는 로그인 후 자신의 여행 계획만 수정 / 삭제할 수 있다.

---

# Phase 12 - 품질 개선

## 구현

- [ ] Global Exception Handler
- [ ] Validation 정리
- [ ] Logging 정책
- [ ] 테스트 보강
- [ ] README 작성
- [ ] API 문서
- [ ] ERD 이미지
- [ ] 아키텍처 이미지
- [ ] Docker 실행 방법 정리

---

# Phase 13 - 배포

## 목표

로컬 환경이 아닌 외부 환경에서 서비스를 실행한다.

## 구현 후보

- [ ] Docker Image
- [ ] 배포 플랫폼 선정
- [ ] 운영 DB
- [ ] local / test / prod 프로필 분리
- [ ] 운영 환경변수 설정
- [ ] Application / DB Health Check
- [ ] AI / 지도 API 상태 및 호출량 관찰
- [ ] CORS
- [ ] 민감 정보 마스킹 운영 로그
- [ ] Flyway migration 배포 절차
- [ ] 배포 전후 검증 체크리스트

## 완료 기준

운영 프로필에서 `ddl-auto: validate`로 migration 적용 결과를 검증하고, 애플리케이션·DB health check와 핵심 API 확인을 마친다. 외부 AI·지도 API 장애가 발생해도 오류가 구분되어 기록되며 API 키와 사용자 원문은 로그에 남지 않는다.

---

# 2. 매 Phase 개발 루틴

각 Phase에서 다음 순서를 지킨다.

### 1. 요구사항 확인

`docs/01-requirements.md`

### 2. 관련 설계 확인

```text
docs/02-architecture.md
docs/03-database.md
docs/04-api-spec.md
```

### 3. Codex에게 설계 설명 요청

코드를 바로 생성시키기보다 먼저 다음을 확인한다.

- 수정할 파일
- 각 클래스 역할
- Spring 개념
- 예상 데이터 흐름

### 4. 작은 단위 구현

예:

```text
Place Entity
→ Repository
→ Service
→ DTO
→ Controller
→ Test
```

### 5. 테스트

기능 구현 직후 테스트한다.

기능을 완료로 표시하기 전 `docs/10-definition-of-done.md`의 공통 및 해당 도메인 체크리스트를 확인한다.

### 6. 결정 기록

설계를 변경했다면 `06-decisions.md`에 기록한다.

### 7. Git Commit

한 커밋에 여러 Phase를 섞지 않는다.

---

# 3. 포트폴리오용 측정 항목

단순히 "구현했다"에서 끝나지 않도록 다음을 기록한다.

## 경로

```text
Nearest Neighbor 거리
2-opt 적용 후 거리
개선 거리
개선율
```

## AI

```text
정상 Parsing 성공률
실패 유형
재시도 정책
Response Format
```

## DB

```text
주요 Query
N+1 발생 여부
Query 개선 전후
```

## 테스트

```text
핵심 Domain Test 수
Service Test
Integration Test
```

실제 수치는 측정한 후에만 README / 자소서에 사용한다.
