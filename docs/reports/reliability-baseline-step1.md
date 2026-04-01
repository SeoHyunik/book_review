# Reliability Baseline Report

## 1. Executive Summary
- `/news` 목록 화면은 요청 시점에 provider를 직접 호출하지 않고, 저장된 `NewsEvent`와 파생 요약을 읽는 구조다.
- request-time fail-open은 `NewsController.list()` 안의 `RuntimeException` 방어에 한정되며, provider fallback은 주로 배치/수집 경로에서 일어난다.
- NAVER/GNews/NewsAPI provider와 selector에는 실패 격리 로직이 있으나, timeout 정책은 이번 점검 범위에서 확인되지 않았다.
- 이번 baseline에서 실행한 Gradle 테스트는 플러그인 해석 단계에서 막혀서 실제 테스트 메서드까지 도달하지 못했다.
- 현재 가장 큰 리스크는 `/news` 런타임 안정성보다도, 검증 자체가 막힌 빌드 설정과 예외 경로 통합 테스트 부재다.

## 2. Current Test Status
- 실행한 명령: `./gradlew.bat --no-daemon --no-configuration-cache test --tests com.example.macronews.controller.NewsControllerTest --tests com.example.macronews.service.forecast.NewsAggregationServiceTest --tests com.example.macronews.service.news.NewsQueryServiceTest --tests com.example.macronews.service.news.NewsApiServiceImplTest --tests com.example.macronews.service.news.source.NewsSourceProviderSelectorTest --tests com.example.macronews.service.news.source.NaverNewsSourceProviderTest --tests com.example.macronews.service.news.source.GNewsSourceProviderTest --tests com.example.macronews.config.PublicNewsAccessIntegrationTest`
- 결과: 실패
- 통과한 테스트: 없음
- 실패한 테스트 메서드: 없음, 테스트 단계까지 진입하지 못함
- 건너뛴 테스트: 없음, 테스트 디스커버리 전에 빌드가 중단됨
- 핵심 실패 메시지:
  - `Plugin [id: 'org.springframework.boot', version: '4.0.0-SNAPSHOT'] was not found`
  - `Build file 'S:\dev\project\auziraum\build.gradle.kts' line: 3`
  - 플러그인 해석 중 `org.springframework.boot:org.springframework.boot.gradle.plugin:4.0.0-SNAPSHOT`을 찾지 못해 빌드가 종료됨

## 3. Real `/news` Execution Path
- 진입점은 `src/main/java/com/example/macronews/controller/NewsController.java:48-93`의 `NewsController.list()`다.
- `list()`는 `resolveStatus()`, `resolveSort()`, `resolvePage()`로 요청 파라미터를 정규화한 뒤 `safeGetRecentNews()`로 목록을 읽는다.
- `safeGetRecentNews()`는 `NewsQueryService.getRecentNews(status, sort)`를 호출하고, `RuntimeException` 발생 시 빈 리스트로 대체한다.
- `NewsQueryService.getRecentNews()`는 `loadCandidates(status)`를 통해 `NewsEventRepository.findTop20ByOrderByIngestedAtDesc()` 또는 `findByStatus(status)`를 읽고, `isDisplayEligible()` 필터와 정렬 후 `NewsListItemDto`로 변환한다.
- `safeGetMarketSignalOverview()`는 `NewsQueryService.getMarketSignalOverview(status, sort)`를 호출하며, 같은 repository-backed candidate set을 사용해 `MarketSignalOverviewDto`를 만든다.
- `safeGetCurrentForecastSnapshot()`은 `MarketForecastQueryService.getCurrentSnapshot()`을 통해 `NewsAggregationService.getCurrentSnapshot()`으로 내려간다.
- `NewsAggregationService.getCurrentSnapshot()`은 `NewsEventRepository.findByStatus(NewsStatus.ANALYZED)`에서 최근 분석 기사를 읽고, OpenAI 호출과 `MarketDataFacade`를 이용해 `MarketForecastSnapshotDto`를 생성한다.
- `safeResolveFeaturedSummarySelection()`은 `MarketSummarySnapshotService.getLatestValidSummary() -> AiMarketSummaryService.getCurrentSummary() -> RecentMarketSummaryService.getCurrentSummary()` 순으로 읽고, 모두 실패하면 article fallback으로 돌아간다.
- `NewsController.list()`는 최종적으로 `src/main/resources/templates/news/list.html`에 `newsItems`, `featuredNews`, `featuredStoredMarketSummary`, `featuredAiMarketSummary`, `featuredMarketSummary`, `marketForecastSnapshot`, `marketSignalOverview`를 주입한다.
- 상세 경로는 `NewsController.detail()`(`src/main/java/com/example/macronews/controller/NewsController.java:157-195`)이며, `NewsQueryService.getNewsDetail(id)`가 비어 있으면 `/news`로 redirect한다.
- provider layer는 `/news` request path에 직접 연결되지 않고, 배치 수집 경로인 `ScheduledNewsIngestionJob.ingestTopHeadlines()` -> `NewsIngestionServiceImpl.ingestTopHeadlines()` -> `NewsSourceProviderSelector.fetchTopHeadlines()`에서 사용된다.
- `NewsSourceProviderSelector.fetchTopHeadlines()`는 configured provider들을 우선순위와 freshness bucket(`FRESH`, `SEMI_FRESH`)으로 나눠 호출하고, 실패한 provider는 catch 후 계속 진행한다.
- 수집 결과는 `NewsIngestionServiceImpl.ingestExternalItem()`에서 `NewsEventRepository.save()`로 저장되며, 이 저장 데이터가 `/news` 목록의 입력이 된다.
- 템플릿/구성 기반 동작은 `src/main/resources/application.yaml`의 `app.news.*`, `app.forecast.*`, `app.featured.market-summary.*` 설정에 의해 달라진다.

## 4. Fail-open and Fallback Verification
- `/news fail-open behavior`: **Confirmed from code**
  - `NewsController.list()`가 `NewsQueryService`, `MarketForecastQueryService`, 요약 선택 로직을 각각 `RuntimeException`으로 감싸고 있다.
  - 실패 시 빈 목록, `null` forecast snapshot, article fallback summary로 계속 렌더링한다.
- provider fallback order: **Confirmed from code**
  - `NewsSourceProviderSelector.currentPriority()`가 서울 시간대와 `app.ingestion.domestic-start-hour`, `app.ingestion.domestic-end-hour`로 domestic/foreign 우선순위를 정한다.
  - `selectConfiguredProviders()`는 `supports(priority)`와 `isConfigured()`를 모두 만족하는 provider만 남긴다.
  - `collectCandidates()`는 provider별 실패를 catch하고 다음 provider로 진행한다.
- exception isolation: **Partially confirmed**
  - provider 호출 예외는 selector에서 격리된다.
  - request-time 예외는 `RuntimeException`만 방어하고 있으므로 `Error`나 아직 확인하지 못한 비동기 예외는 범위 밖이다.
- timeout handling: **Not confirmed from code**
  - `NewsController`, `NewsAggregationService`, `NewsSourceProviderSelector`, `NaverNewsSourceProvider`, `GNewsSourceProvider`, `NewsApiServiceImpl`에서 명시적 timeout/retry 설정은 확인되지 않았다.
  - 실제 timeout 정책은 `ExternalApiUtils` 구현을 추가로 봐야 확정 가능하다.
- whether NAVER failure can break the endpoint: **Confirmed from code**
  - `/news` 목록은 NAVER provider를 직접 호출하지 않는다.
  - NAVER provider가 실패해도 selector가 예외를 삼키고, 목록 화면은 repository-backed 데이터로 렌더링된다.
- whether aggregation degrades gracefully: **Confirmed from code**
  - `NewsAggregationService.getCurrentSnapshot()`은 뉴스 부족, OpenAI 미설정, 비정상 응답, 예외 발생 시 `Optional.empty()`를 반환한다.
  - `MarketForecastQueryService`와 `NewsController.list()`는 그 결과를 `null` 또는 기본 화면으로 흡수한다.

## 5. Production Blockers
- **P1**
  - issue: Spring Boot Gradle plugin `4.0.0-SNAPSHOT`을 현재 환경에서 해석하지 못해 테스트/검증이 시작되지 않는다.
  - impact: `/news` 안정성 검증과 회귀 테스트가 모두 막혀 baseline을 고정할 수 없다.
  - evidence: `./gradlew.bat ... test ...`가 `build.gradle.kts:3`의 플러그인 해석 단계에서 종료되었다.
  - likely fix area: `build.gradle.kts`, plugin repository 접근성, 또는 resolvable build version 정리.
- **P1**
  - issue: `/news` 요청의 예외 경로를 실제 통합 테스트로 검증하지 못했다.
  - impact: `NewsQueryService` 또는 summary 서비스가 runtime exception을 던질 때 public page fail-open 보장이 regress될 수 있다.
  - evidence: `NewsControllerTest`는 happy path 모델 검증 위주이고, `PublicNewsAccessIntegrationTest`도 mock 기반 200 OK 확인에 머문다.
  - likely fix area: `src/test/java/com/example/macronews/config/PublicNewsAccessIntegrationTest.java`, `src/test/java/com/example/macronews/controller/NewsControllerTest.java`.
- **P2**
  - issue: provider failure isolation의 부정형 테스트가 부족하다.
  - impact: selector/provider fallback 순서 변경 시 한 provider 장애가 수집 전체를 막는 회귀를 놓칠 수 있다.
  - evidence: `NewsSourceProviderSelectorTest`는 정상 fallback과 freshness/dedup 경로만 검증하고, provider가 exception을 던지는 경우는 다루지 않는다.
  - likely fix area: `src/test/java/com/example/macronews/service/news/source/NewsSourceProviderSelectorTest.java`, `src/test/java/com/example/macronews/config/ScheduledNewsIngestionJobTest.java`.

## 6. Documentation Mismatch
- README.md는 현재 테스트 상황을 “실패하고 있다”는 식으로 단정하지만, 이번 baseline에서 실제로 확인된 것은 테스트 실패가 아니라 Gradle plugin resolution 실패였다.
- README.md와 PROJECT_BRIEF.md는 `/news` 공용 페이지의 fail-open 성격을 강조하지만, 코드상 request-time 방어는 `NewsController.list()` 내부의 `RuntimeException` 범위에 한정된다.
- provider fallback은 request path가 아니라 ingestion/scheduler path에서 일어난다. 문서가 이를 `/news` 직접 보장으로 읽히게 만들면 과장이다.
- PROJECT_BRIEF.md는 최소 required indicators로 `USD/KRW`, `US 10Y Treasury Yield`, `DXY`, `WTI/Brent`, `KOSPI`를 제시하지만, `NewsAggregationService.resolveMarketContext()`는 현재 `USD/KRW`, `Gold`, `WTI`, `Brent`, `KOSPI`만 주입한다. `DXY`와 `US 10Y Treasury Yield`는 아직 구현되지 않았다.
- 그 외 핵심 제품 범위(뉴스 해석, 시장 요약, snapshot/forecast 보조 기능)는 코드와 대체로 일치한다.

## 7. Recommended Next Implementation Order
1. `/news` list fail-open 통합 테스트 추가
   - why it matters: public page의 가장 중요한 안정성 보장을 실제로 검증해야 한다.
   - target files/classes: `src/test/java/com/example/macronews/config/PublicNewsAccessIntegrationTest.java`, `src/test/java/com/example/macronews/controller/NewsControllerTest.java`, `src/main/java/com/example/macronews/controller/NewsController.java`.
   - required test coverage: `NewsQueryService`/`MarketForecastQueryService`/summary 서비스가 예외를 던져도 `/news`가 200으로 렌더링되고 fallback model이 유지되는지.
   - risk level: 높음
2. provider exception fallback 테스트 추가
   - why it matters: 한 provider 장애가 수집 전체를 깨지 않는다는 보장이 필요하다.
   - target files/classes: `src/test/java/com/example/macronews/service/news/source/NewsSourceProviderSelectorTest.java`, `src/test/java/com/example/macronews/config/ScheduledNewsIngestionJobTest.java`.
   - required test coverage: preferred provider가 `RuntimeException`을 던져도 다음 provider가 선택되고 batch ingestion이 계속되는지.
   - risk level: 중간
3. NAVER/GNews 장애 경로 회귀 테스트 보강
   - why it matters: 외부 API 장애와 파싱 실패는 가장 빈번한 운영 리스크다.
   - target files/classes: `src/test/java/com/example/macronews/service/news/source/NaverNewsSourceProviderTest.java`, `src/test/java/com/example/macronews/service/news/source/GNewsSourceProviderTest.java`, `src/test/java/com/example/macronews/service/news/NewsApiServiceImplTest.java`.
   - required test coverage: 5xx, 빈 body, malformed JSON, stale data, semi-fresh fallback이 모두 의도대로 empty/fallback 처리되는지.
   - risk level: 중간
4. Gradle plugin resolution 문제 정리
   - why it matters: 검증이 막힌 상태에서는 baseline도, 회귀 확인도 불가능하다.
   - target files/classes: `build.gradle.kts`, 필요 시 `settings.gradle.kts`.
   - required test coverage: `./gradlew test`가 실제 테스트 디스커버리 단계까지 진입하는지.
   - risk level: 높음
