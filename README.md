# Macro News Interpretation

한국어 사용자에게 최근 매크로/시장 뉴스를 빠르게 해석해 주기 위한 Spring Boot + Thymeleaf 기반 모놀리식 서비스입니다.

이 프로젝트는 단순 뉴스 포털이 아니라, 외부 뉴스 수집 결과를 저장하고 AI 해석을 붙인 뒤, 최근 기사 묶음을 다시 요약해 시장 내러티브와 포캐스트 형태로 보여주는 것을 목표로 합니다.

현재 저장소 기준으로 다음 성격을 갖습니다.

- 한국어 중심 매크로 뉴스 해석 플랫폼
- 기사 단위 해석 + 최근 뉴스 묶음 기반 요약/예측
- Spring Boot + MongoDB + Thymeleaf 모놀리스
- 운영 안정성과 추적 가능한 흐름을 우선하는 구조

## 1. 프로젝트 소개

Macro News Interpretation(MNI)은 한국어 사용자를 위한 거시경제/시장 해석 서비스입니다.

핵심 목적은 다음과 같습니다.

- 흩어진 뉴스 헤드라인을 구조화된 해석으로 바꾸기
- 최근 시장을 지배하는 매크로 내러티브를 빠르게 보여주기
- 금리, 달러, 유가, 인플레이션, 지정학, 리스크 심리 같은 핵심 드라이버를 요약하기
- 여러 기사를 묶어 홈페이지형 시장 브리핑과 단기 포캐스트로 압축하기

현재 제품 방향은 [PROJECT_BRIEF.md](./PROJECT_BRIEF.md)를 따르며, “뉴스 기반 매크로 해석 + 시장 내러티브 합성”이 중심입니다.

## 2. 주요 기능

현재 코드 기준으로 실제 구현되어 있는 주요 기능입니다.

- 공개 뉴스 목록 페이지 `/news`
- 뉴스 상세 페이지 `/news/{id}`
- AI 해석 결과를 반영한 제목/요약 표시
- 최근 분석 기사 기반 시장 시그널 보드
- 최근 분석 기사 기반 마켓 포캐스트 스냅샷
- 현재 마켓 요약 페이지 `/market-summary/current`
- 저장된 마켓 요약 상세 페이지 `/market-summary/{id}`
- 회원가입, 로그인, 사용자명/이메일 중복 확인
- 익명 사용자의 뉴스 상세보기 횟수 제한 후 로그인 유도
- 관리자 수동 기사 등록 및 즉시 해석
- 관리자 외부 뉴스 수집 실행
- 자동 수집 스케줄 제어
- Keep-alive / 이메일 알림 런타임 토글
- OpenAI 사용량 및 비용 대시보드

현재 연결된 외부 데이터 소스는 다음과 같습니다.

- 글로벌 뉴스: NewsAPI
- 국내 뉴스: NAVER News Search
- 보조 글로벌 뉴스: GNews
- 시장 데이터: USD/KRW, 금, 유가

## 3. 시스템 아키텍처

프로젝트는 전형적인 Spring Boot 모놀리스 구조를 유지합니다.

- `controller`
  사용자/관리자 요청 처리와 페이지 라우팅
- `service`
  뉴스 수집, AI 해석, 요약, 포캐스트, 운영 기능 처리
- `provider`
  외부 뉴스/시장 데이터 공급자 구현
- `repository`
  MongoDB 접근 계층
- `templates`
  Thymeleaf 기반 서버 렌더링 UI

대표적인 구성 흐름은 다음과 같습니다.

- `NewsController`
  공개 뉴스 목록/상세 페이지 렌더링
- `AdminNewsController`
  수동/자동 수집, 재해석, 삭제, 운영 토글
- `NewsIngestionServiceImpl`
  외부 뉴스 저장 및 비동기 해석 트리거
- `MacroAiServiceImpl`
  기사 단위 AI 해석
- `NewsAggregationService`
  최근 분석 기사 기반 포캐스트 생성
- `AiMarketSummaryService`
  홈페이지형 AI 시장 요약 생성
- `MarketSummarySnapshotService`
  저장된 요약 스냅샷 조회 및 갱신

## 4. 데이터 흐름

현재 저장소 기준 핵심 데이터 흐름은 아래와 같습니다.

1. 뉴스 공급자 선택
   `NewsSourceProviderSelector`가 서울 시간 기준으로 국내/해외 우선순위를 정합니다.
2. 외부 뉴스 수집
   NewsAPI, NAVER, GNews 중 설정된 공급자에서 기사를 가져옵니다.
3. 뉴스 저장
   `NewsIngestionServiceImpl`가 중복을 확인하고 `NewsEvent`를 저장합니다.
4. 기사 단위 AI 해석
   `MacroAiServiceImpl`가 각 기사에 대해 `AnalysisResult`를 생성합니다.
5. 최근 분석 기사 재집계
   `NewsQueryService`, `NewsAggregationService`, `RecentMarketSummaryService`가 최근 기사를 다시 읽어 목록/시그널/요약 입력을 만듭니다.
6. 포캐스트 및 마켓 요약 생성
   최근 분석 기사 클러스터를 이용해 포캐스트와 featured summary를 생성합니다.
7. UI 렌더링
   Thymeleaf가 `/news`, `/market-summary`, `/market-forecast`, 관리자 화면을 렌더링합니다.

공개 메인 흐름은 `/news`에 집중되어 있습니다.

- 뉴스 목록
- 시장 시그널
- 포캐스트 스냅샷
- featured market summary
- 기사 상세 이동

## 5. 기술 스택

- Java 25
- Spring Boot 4.x Snapshot
- Spring MVC
- Spring Security
- Spring Data MongoDB
- Thymeleaf
- Gradle Kotlin DSL
- OpenAI API
- Log4j2
- JUnit 5 / Mockito / Spring Test / Testcontainers 일부

운영/외부 연동 관련 요소:

- MongoDB
- NewsAPI
- NAVER Search API
- GNews API
- ExchangeRate API
- MetalPrice API
- OilPrice API

## 6. 실행 방법

### 기본 실행

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 포트는 `8080`입니다.

### 필수/주요 환경 변수

실행 환경에 따라 아래 설정이 필요합니다.

- MongoDB
  `MONGODB_URI`
- OpenAI
  `OPENAI_API_KEY`, `OPENAI_API_URL`, `OPENAI_MODEL`
- 글로벌 뉴스
  `NEWS_API_KEY`
- NAVER 뉴스
  `APP_NEWS_NAVER_ENABLED`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
- GNews
  `APP_NEWS_GNEWS_API_KEY`
- 시장 데이터
  `EXCHANGE_RATE_API_KEY`, `METALPRICE_API_KEY`, `OILPRICE_API_KEY`

관리자/운영 관련:

- 관리자 부트스트랩
  `APP_ADMIN_ALLOWED_USERNAMES`, `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_PASSWORD`, `APP_BOOTSTRAP_ADMIN_EMAIL`
- 자동 수집
  `APP_INGESTION_SCHEDULER_ENABLED`, `APP_INGESTION_SCHEDULER_CRON`, `APP_INGESTION_SCHEDULER_PAGE_SIZE`
- Keep-alive
  `APP_KEEP_ALIVE_ENABLED`, `APP_KEEP_ALIVE_TARGET_URL`
- 이메일 알림
  `APP_NOTIFICATION_EMAIL_ENABLED`, `APP_NOTIFICATION_EMAIL_RECIPIENT`, `APP_NOTIFICATION_EMAIL_FROM`

### 테스트 실행

```bash
./gradlew test
```

현재 저장소 기준으로 일부 테스트는 실패하고 있습니다. 공개 `/news`의 선택적 요약/포캐스트 의존성 실패는 fail-open 방향으로 보강되었지만, NAVER 공급자 테스트를 포함한 전체 테스트 스위트는 아직 모두 안정화되지 않았습니다.

## 7. 현재 상태

### 구현된 것

- 뉴스 수집 파이프라인의 기본 구조
- 외부 뉴스 저장 및 중복 제거
- 기사 단위 AI 해석
- 공개 뉴스 목록/상세 UI
- 최근 뉴스 기반 시그널 집계
- 최근 뉴스 기반 포캐스트 생성
- 최근 뉴스 기반 featured market summary 생성
- 저장형 market summary snapshot
- 관리자 수동/자동 수집 화면
- 관리자 재해석, 삭제, 일괄 삭제
- OpenAI 사용량 기록 및 관리자 대시보드
- 다국어 전환 기반 UI
- 익명 상세보기 제한

### 미완성 또는 주의가 필요한 부분

- 공개 `/news` 페이지 신뢰성
  선택적 요약/포캐스트 조회 실패 시에도 목록 페이지는 기본 모드로 렌더링되도록 보강되었습니다. 다만 공개 경로 전반의 통합 회귀 커버리지는 더 필요합니다.
- NAVER 공급자 안정성
  관련 테스트가 일부 실패하고 있어 필터/신선도/쿼리 동작 조정이 더 필요합니다.
- 가격 인지형 해석 확장
  USD/KRW, 금, 유가 연동은 있으나, 제품 브리프에서 다음 단계로 제시한 DXY, 미국 10년물, KOSPI 실시간 연동은 아직 완성되지 않았습니다.
- 인덱스 시세 연동
  `TwelveDataIndexQuoteProvider`는 아직 실제 구현이 없습니다.
- SEO 확장 페이지
  topic page, archive page, forecast review page 같은 탐색형 페이지는 아직 없습니다.
- Google OAuth
  코드 경로는 일부 있으나 기본 설정은 비활성 상태입니다.

## 8. 향후 방향

이 프로젝트의 다음 방향은 [PROJECT_BRIEF.md](/abs/path/C:/DEV/project/auziraum/PROJECT_BRIEF.md)에 맞춰 아래처럼 보는 것이 적절합니다.

- 공개 페이지 안정성 확보
  `/news`의 선택적 부가 기능은 fail-open으로 보강됐지만, 공개 페이지 전반에서 비핵심 의존성 실패를 더 촘촘히 격리할 필요가 있습니다.
- 수집 파이프라인 안정화
  NAVER/글로벌 뉴스 소스의 신선도와 relevance 로직을 다듬는 작업이 필요합니다.
- 해석 일관성 개선
  기사 단위 해석과 요약/포캐스트 간 문장/방향 일관성을 높이는 작업이 필요합니다.
- 최소한의 가격 데이터 결합
  USD/KRW, 유가, 금 수준을 넘어 KOSPI, DXY, 미국 10년물 같은 핵심 지표와 연결하는 단계가 남아 있습니다.
- SEO 구조 확장
  시장 요약, 아카이브, 주제별 탐색 페이지로 확장할 여지가 있습니다.
- 모놀리스 유지 하의 점진적 UX 개선
  불필요한 프론트엔드 재작성 없이 Thymeleaf 중심으로 탐색성과 신뢰성을 높이는 방향입니다.

## 문서 원칙

이 README는 현재 저장소의 실제 구현 상태를 기준으로 작성했습니다.

- 구현된 기능만 기능으로 설명했습니다.
- 아직 비어 있거나 불안정한 부분은 별도로 구분했습니다.
- 제품 방향은 반영하되, 아직 없는 기능을 이미 제공 중인 것처럼 쓰지 않았습니다.
