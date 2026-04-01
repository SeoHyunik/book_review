# Reliability Baseline Report

## 1. Executive Summary
- `/news` 리스트는 컨트롤러와 일부 서비스 경계에서 fail-open이 걸려 있어, 단일 부가 의존성 실패가 곧바로 500으로 이어지지는 않습니다.
- provider selector와 개별 NAVER/GNews/NewsAPI 구현은 서로의 실패를 흡수하는 방향으로 작성되어 있습니다.
- 다만 외부 호출에 대한 명시적 timeout 설정이 코드상 보이지 않아, 느린 외부 API가 요청 스레드를 묶을 수 있습니다.
- 이번 기준의 테스트는 Gradle 플러그인 해석 단계에서 막혀 실제 test execution 결과를 확보하지 못했습니다.
- 문서에는 현재 코드보다 더 넓은 fail-open 인상과 더 불안정한 테스트 상태가 함께 적혀 있어, 일부 표현은 보수적으로 조정해야 합니다.

## 2. Current Test Status
- 실행한 명령: `./gradlew.bat --no-daemon --no-configuration-cache test --tests com.example.macronews.controller.NewsControllerTest --tests com.example.macronews.service.forecast.NewsAggregationServiceTest --tests com.example.macronews.service.news.source.NewsSourceProviderSelectorTest --tests com.example.macronews.service.news.source.NaverNewsSourceProviderTest --tests com.example.macronews.service.news.source.GNewsSourceProviderTest --tests com.example.macronews.service.news.NewsApiServiceImplTest --tests com.example.macronews.service.news.NewsQueryServiceTest`
- 결과: 실패
- 실행된 테스트 클래스/메서드: 없음, Gradle 설정 단계에서 중단됨
- 실패한 테스트: 없음
- 핵심 실패 메시지: `Plugin [id: 'org.springframework.boot', version: '4.0.0-SNAPSHOT'] was not found`
- 원인 추정: `build.gradle.kts:5`의 snapshot 플러그인 해석 실패이며, 외부 의존성 해석이 끝나지 않아 테스트 스위트 자체가 시작되지 못함
- 재현성: 결정적
- broader suite / full suite: 동일한 빌드 블로커 때문에 추가 실행은 실익이 낮아 미실행

## 3. Real `/news` Execution Path
- 진입 컨트롤러는 `src/main/java/com/example/macronews/controller/NewsController.java:49-86`의 `list(...)` 메서드입니다.
- `list(...)`는 `NewsQueryService.getRecentNews(...)`, `NewsQueryService.getMarketSignalOverview(...)`, `MarketForecastQueryService.getCurrentSnapshot()`, `safeResolveFeaturedSummarySelection()`을 순서대로 호출합니다.
- `safeGetRecentNews(...)` / `safeGetMarketSignalOverview(...)` / `safeGetCurrentForecastSnapshot()` / `safeResolveFeaturedSummarySelection()`은 각각 `RuntimeException`을 잡아서 빈 목록, 빈 오버뷰, `null`, article fallback으로 내려줍니다.
- featured summary 우선순위는 `resolveFeaturedSummarySelection()` 내부에서 `MarketSummarySnapshotService.getLatestValidSummary()` -> `AiMarketSummaryService.getCurrentSummary()` -> `RecentMarketSummaryService.getCurrentSummary()` -> article fallback 순서입니다.
- `NewsQueryService`는 `src/main/java/com/example/macronews/service/news/NewsQueryService.java:117-128,202-226`에서 `NewsEventRepository.findTop20ByOrderByIngestedAtDesc()` 또는 `findByStatus(...)`를 조회한 뒤 정렬/투영만 수행합니다.
- `MarketForecastQueryService`는 `src/main/java/com/example/macronews/service/forecast/MarketForecastQueryService.java:17-40`에서 `NewsAggregationService.getCurrentSnapshot()`에 위임합니다.
- `NewsAggregationService`는 `src/main/java/com/example/macronews/service/forecast/NewsAggregationService.java:98-146,152-264`에서 `NewsEventRepository.findByStatus(NewsStatus.ANALYZED)`로 뉴스 후보를 읽고, `ExternalApiUtils.callAPI(...)`로 OpenAI에 요청하며, `MarketDataFacade`로 시장 컨텍스트를 붙입니다.
- `/news`에 직접 렌더링되는 뷰는 `news/list`이며, 상세 경로는 `news/detail`입니다. 상세 경로는 `NewsController.detail(...)`에서 `NewsQueryService.getNewsDetail(id)`를 조회한 뒤 Thymeleaf 모델을 채웁니다.
- provider/write-side 지원 경로는 `src/main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java:33-83` -> `NewsIngestionService.ingestTopHeadlines(...)` -> `NewsIngestionServiceImpl.loadScheduledHeadlineFeed(...)` -> `NewsSourceProviderSelector.fetchTopHeadlines(...)` -> 개별 provider(`NaverNewsSourceProvider`, `NewsApiServiceImpl`, `GNewsSourceProvider`) -> `NewsEventRepository.save(...)` 순서입니다.
- 즉, `/news` 리스트 자체는 repository 읽기 경로이고, provider 계층은 ingestion 경로를 통해 저장된 뉴스 공급을 담당합니다.

## 4. Fail-open and Fallback Verification
- `/news` fail-open behavior: 부분적으로 확인됨. `NewsController.list(...)`는 `safeGetRecentNews(...)`, `safeGetMarketSignalOverview(...)`, `safeGetCurrentForecastSnapshot()`, `safeResolveFeaturedSummarySelection()`에서 예외를 흡수합니다. 다만 `NewsController.detail(...)`은 동일한 안전 래퍼가 없고, public detail route 전체를 fail-open으로 보장하지는 않습니다.
- provider fallback order: 코드에서 확인됨. `NewsSourceProviderSelector.fetchTopHeadlines(...)`는 국내 우선 시 NAVER를 먼저, 이후 foreign fallback을 사용하며, foreign 그룹에서는 `newsapi-global`을 `gnews-global`보다 먼저 처리합니다.
- exception isolation: 부분적으로 확인됨. `NewsSourceProviderSelector.collectCandidates(...)`는 provider별 예외를 잡고 계속 진행합니다. `NewsAggregationService.generateCurrentSnapshot()`도 OpenAI 호출/파싱 실패를 잡아 `Optional.empty()`로 반환합니다. 다만 `ExternalApiUtils.callAPI()` 자체는 `.block()` 기반이라 장시간 지연에 대한 격리가 보이지 않습니다.
- timeout handling: 코드에서 확인되지 않음. `src/main/java/com/example/macronews/util/ExternalApiUtils.java:27-57`에는 timeout 파라미터가 없고, `src/main/java/com/example/macronews/config/WebClientConfig.java:12-22`도 buffer size만 설정합니다.
- whether NAVER failure can break the endpoint: 코드상 직접적으로는 아니오. `NaverNewsSourceProvider.fetchTopHeadlines(...)`는 비구성/HTTP 실패/파싱 실패 시 빈 리스트를 반환하고, selector는 provider 예외를 잡습니다. `/news` 리스트는 그 결과가 비어도 200으로 렌더링됩니다.
- whether aggregation degrades gracefully: 부분적으로 확인됨. provider와 forecast/featured summary 경로는 빈 값으로 degrade합니다. 다만 detail route와 timeout 부재는 graceful degradation의 범위 밖입니다.

## 5. Production Blockers
- P1
  - issue: Gradle이 `org.springframework.boot:4.0.0-SNAPSHOT` 플러그인을 해석하지 못해 테스트와 검증이 시작되지 않음
  - impact: 현재 상태에서는 reliability baseline을 CI 수준에서 재현할 수 없고, 어떤 변경도 검증되지 않음
  - evidence: `build.gradle.kts:5,71` 및 `Plugin [id: 'org.springframework.boot', version: '4.0.0-SNAPSHOT'] was not found`
  - likely fix area: build/plugin management, Spring Boot plugin version pinning 또는 저장소 접근성
- P1
  - issue: 외부 HTTP 호출에 명시적 timeout이 없음
  - impact: NAVER, NewsAPI, GNews, OpenAI, market data 중 하나가 느리거나 멈추면 `/news` 리스트의 forecast/summary 경로와 ingestion worker가 장시간 블로킹될 수 있음
  - evidence: `ExternalApiUtils.java:27-57`, `WebClientConfig.java:12-22`
  - likely fix area: `WebClient` timeout 설정, `ExternalApiUtils` 호출 래퍼, 필요 시 circuit breaker
- P2
  - issue: public detail route(`/news/{id}`)는 list route처럼 안전 래퍼가 없음
  - impact: repository나 view model 계산에서 예상치 못한 런타임이 나면 500으로 노출될 수 있음
  - evidence: `NewsController.java:158-196`와 `GlobalExceptionHandler.java:90-115`
  - likely fix area: `NewsController.detail(...)`의 defensive fallback 또는 detail service 경계의 예외 격리
- P2
  - issue: 실제 `/news` end-to-end reliability를 검증하는 통합 테스트가 부족함
  - impact: provider fallback, repository read, forecast snapshot, Thymeleaf 렌더링의 조합 회귀를 놓치기 쉬움
  - evidence: `PublicNewsAccessIntegrationTest.java:18-45`는 `NewsQueryService`와 `MarketForecastQueryService`를 mock 처리함
  - likely fix area: 실제 Mongo/Testcontainers + 외부 HTTP mock server를 사용하는 `/news` 통합 테스트

## 6. Documentation Mismatch
- README.md는 “현재 저장소 기준으로 일부 테스트는 실패하고 있습니다”라고 적지만, 이번 baseline에서는 테스트 실패가 아니라 Gradle snapshot 플러그인 해석 실패로 테스트가 시작조차 되지 않았습니다. 즉 테스트 불안정성 설명이 실제 실패 양상보다 좁고, 원인이 다릅니다. (`README.md:185`)
- README.md와 PROJECT_BRIEF.md는 공통적으로 public pages fail gracefully / `/news` must not fail hard를 강조하지만, 코드상 그 보장은 `NewsController.list(...)` 중심이고 `NewsController.detail(...)`까지 동일하게 적용되지는 않습니다. (`README.md:225`, `PROJECT_BRIEF.md:152,159`, `NewsController.java:49-86,158-196`)
- README.md의 “fail-open 방향으로 보강되었다”는 표현은 list route와 일부 optional dependency에 대해서는 맞지만, timeout 부재와 detail route의 예외 격리 부족까지 포함하면 아직 전면적 보장이라고 보기 어렵습니다. (`README.md:185,225`)
- AGENTS.md는 현재 코드 상태와 충돌하는 실질적 기능 서술을 거의 하지 않아서, 의미 있는 mismatch는 보이지 않았습니다.

## 7. Recommended Next Implementation Order
1. 빌드 플러그인 해석 문제를 먼저 해소한다.
   - 왜 중요한가: 테스트와 검증이 시작되지 않으면 어떤 reliability 작업도 재현 불가능하다.
   - target files/classes: `build.gradle.kts`, 필요 시 plugin management 설정
   - required test coverage: `./gradlew test`가 실제로 시작되는지, 최소한 `NewsControllerTest`와 `NewsSourceProviderSelectorTest`가 실행되는지 확인
   - risk level: High
2. 외부 HTTP timeout을 명시적으로 추가한다.
   - 왜 중요한가: 느린 provider/OpenAI 호출이 `/news`와 ingestion worker를 블로킹하는 가장 큰 운영 리스크다.
   - target files/classes: `src/main/java/com/example/macronews/util/ExternalApiUtils.java`, `src/main/java/com/example/macronews/config/WebClientConfig.java`, 필요 시 `application.yaml`
   - required test coverage: timeout/slow-response 시 `ExternalApiUtils`가 빠르게 실패-open하는지, `NewsAggregationServiceTest`와 provider tests가 지연 실패를 흡수하는지
   - risk level: High
3. `/news/{id}` detail 경로에도 fail-open 경계를 추가한다.
   - 왜 중요한가: public route 전체를 reliability 대상이라고 보면 list만 안전한 것은 부족하다.
   - target files/classes: `src/main/java/com/example/macronews/controller/NewsController.java`
   - required test coverage: `NewsControllerTest`에 detail service exception, invalid model data, missing id 케이스 추가
   - risk level: Medium
4. 실제 `/news` 통합 테스트를 보강한다.
   - 왜 중요한가: 현재 공개 접근 테스트는 mock 기반이라 provider fallback과 repository read path를 함께 검증하지 못한다.
   - target files/classes: `src/test/java/com/example/macronews/config/PublicNewsAccessIntegrationTest.java`, 신규 integration test
   - required test coverage: Mongo-backed list render, provider failure 시 empty/partial feed, forecast snapshot absence 시 fallback render
   - risk level: Medium

## 8. Step 2 Build Recovery Result
- root cause of plugin resolution failure: `build.gradle.kts`에 선언된 `org.springframework.boot` 플러그인 버전 `4.0.0-SNAPSHOT`이 현재 설정된 플러그인 저장소에서 해석되지 않아, Gradle이 루트 프로젝트 설정 단계에서 중단되었다.
- exact files changed: `build.gradle.kts`, `docs/reports/reliability-baseline-step1.md`
- what was changed and why: Spring Boot 플러그인 버전을 `4.0.3`으로 변경했다. Java 25와 Spring Boot 4.x+ baseline은 유지하면서, snapshot 미해결로 막히던 플러그인 해석만 복구하기 위한 최소 변경이다.
- verification commands run:
  - `./gradlew.bat --no-daemon --no-configuration-cache help`
  - `./gradlew.bat --no-daemon --no-configuration-cache test --tests com.example.macronews.controller.NewsControllerTest --tests com.example.macronews.service.forecast.NewsAggregationServiceTest --tests com.example.macronews.service.news.NewsQueryServiceTest --tests com.example.macronews.service.news.NewsApiServiceImplTest --tests com.example.macronews.service.news.source.NewsSourceProviderSelectorTest --tests com.example.macronews.service.news.source.NaverNewsSourceProviderTest --tests com.example.macronews.service.news.source.GNewsSourceProviderTest --tests com.example.macronews.config.PublicNewsAccessIntegrationTest`
- whether tests reached discovery/execution: 확인됨. `help`가 성공했고, `test`는 `compileJava`, `compileTestJava`, `testClasses`를 통과한 뒤 실제 테스트 실행 단계까지 진입했다.
- exact passing/failing test classes:
  - passed: `NewsControllerTest`, `NewsAggregationServiceTest`, `NewsQueryServiceTest`, `NewsApiServiceImplTest`, `NewsSourceProviderSelectorTest`, `GNewsSourceProviderTest`, `PublicNewsAccessIntegrationTest`
  - failed: `NaverNewsSourceProviderTest` (9개 실패)
  - failing methods:
    - `NAVER provider should parse alternate pubDate formats safely`
    - `NAVER provider should use link when original link is missing`
    - `NAVER provider should fetch a later page when page one is stale only`
    - `NAVER provider should not use links as summary text`
    - `NAVER semi-fresh bucket should allow controlled fallback items`
    - `NAVER provider should keep normal descriptions unchanged`
    - `NAVER provider should stop paging once enough fresh items are found`
    - `NAVER provider should use built-in default queries when configured queries are blank`
    - `NAVER provider should use built-in default queries when configured queries are whitespace only`
- remaining blockers: build 설정은 복구되었지만 `NaverNewsSourceProviderTest`에 기존 회귀/정합성 실패가 남아 있어 전체 테스트 녹색 상태는 아니다. 현재 차수에서는 `/news` 동작 변경 없이 build/test 실행 가능 상태만 복구했다.
