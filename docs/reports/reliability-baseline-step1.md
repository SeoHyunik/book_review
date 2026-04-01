# Reliability Baseline Report

## 1. Executive Summary
- `/news` 由ъ뒪?몃뒗 而⑦듃濡ㅻ윭? ?쇰? ?쒕퉬??寃쎄퀎?먯꽌 fail-open??嫄몃젮 ?덉뼱, ?⑥씪 遺媛 ?섏〈???ㅽ뙣媛 怨㏓컮濡?500?쇰줈 ?댁뼱吏吏???딆뒿?덈떎.
- provider selector? 媛쒕퀎 NAVER/GNews/NewsAPI 援ы쁽? ?쒕줈???ㅽ뙣瑜??≪닔?섎뒗 諛⑺뼢?쇰줈 ?묒꽦?섏뼱 ?덉뒿?덈떎.
- ?ㅻ쭔 ?몃? ?몄텧?????紐낆떆??timeout ?ㅼ젙??肄붾뱶??蹂댁씠吏 ?딆븘, ?먮┛ ?몃? API媛 ?붿껌 ?ㅻ젅?쒕? 臾띠쓣 ???덉뒿?덈떎.
- ?대쾲 湲곗????뚯뒪?몃뒗 Gradle ?뚮윭洹몄씤 ?댁꽍 ?④퀎?먯꽌 留됲? ?ㅼ젣 test execution 寃곌낵瑜??뺣낫?섏? 紐삵뻽?듬땲??
- 臾몄꽌?먮뒗 ?꾩옱 肄붾뱶蹂대떎 ???볦? fail-open ?몄긽怨???遺덉븞?뺥븳 ?뚯뒪???곹깭媛 ?④퍡 ?곹? ?덉뼱, ?쇰? ?쒗쁽? 蹂댁닔?곸쑝濡?議곗젙?댁빞 ?⑸땲??

## 2. Current Test Status
- ?ㅽ뻾 1: `./gradlew.bat --no-daemon --no-configuration-cache test --tests com.example.macronews.controller.NewsControllerTest --tests com.example.macronews.service.forecast.NewsAggregationServiceTest --tests com.example.macronews.service.news.source.NewsSourceProviderSelectorTest --tests com.example.macronews.service.news.source.NaverNewsSourceProviderTest --tests com.example.macronews.service.news.source.GNewsSourceProviderTest --tests com.example.macronews.service.news.NewsApiServiceImplTest --tests com.example.macronews.service.news.NewsQueryServiceTest`
- 寃곌낵: ?ㅽ뙣
- ?ㅽ뻾???뚯뒪???대옒??硫붿꽌?? ?놁쓬, Gradle ?ㅼ젙 ?④퀎?먯꽌 以묐떒??- ?ㅽ뙣???뚯뒪?? ?놁쓬
- ?듭떖 ?ㅽ뙣 硫붿떆吏: `Plugin [id: 'org.springframework.boot', version: '4.0.0-SNAPSHOT'] was not found`
- ?먯씤 異붿젙: `build.gradle.kts:5`??snapshot ?뚮윭洹몄씤 ?댁꽍 ?ㅽ뙣?대ŉ, ?몃? ?섏〈???댁꽍???앸굹吏 ?딆븘 ?뚯뒪???ㅼ쐞???먯껜媛 ?쒖옉?섏? 紐삵븿
- ?ы쁽?? 寃곗젙??- broader suite / full suite: ?숈씪??鍮뚮뱶 釉붾줈而??뚮Ц??異붽? ?ㅽ뻾? ?ㅼ씡????븘 誘몄떎??
## 3. Real `/news` Execution Path
- 吏꾩엯 而⑦듃濡ㅻ윭??`src/main/java/com/example/macronews/controller/NewsController.java:49-86`??`list(...)` 硫붿꽌?쒖엯?덈떎.
- `list(...)`??`NewsQueryService.getRecentNews(...)`, `NewsQueryService.getMarketSignalOverview(...)`, `MarketForecastQueryService.getCurrentSnapshot()`, `safeResolveFeaturedSummarySelection()`???쒖꽌?濡??몄텧?⑸땲??
- `safeGetRecentNews(...)` / `safeGetMarketSignalOverview(...)` / `safeGetCurrentForecastSnapshot()` / `safeResolveFeaturedSummarySelection()`? 媛곴컖 `RuntimeException`???≪븘??鍮?紐⑸줉, 鍮??ㅻ쾭酉? `null`, article fallback?쇰줈 ?대젮以띾땲??
- featured summary ?곗꽑?쒖쐞??`resolveFeaturedSummarySelection()` ?대??먯꽌 `MarketSummarySnapshotService.getLatestValidSummary()` -> `AiMarketSummaryService.getCurrentSummary()` -> `RecentMarketSummaryService.getCurrentSummary()` -> article fallback ?쒖꽌?낅땲??
- `NewsQueryService`??`src/main/java/com/example/macronews/service/news/NewsQueryService.java:117-128,202-226`?먯꽌 `NewsEventRepository.findTop20ByOrderByIngestedAtDesc()` ?먮뒗 `findByStatus(...)`瑜?議고쉶?????뺣젹/?ъ쁺留??섑뻾?⑸땲??
- `MarketForecastQueryService`??`src/main/java/com/example/macronews/service/forecast/MarketForecastQueryService.java:17-40`?먯꽌 `NewsAggregationService.getCurrentSnapshot()`???꾩엫?⑸땲??
- `NewsAggregationService`??`src/main/java/com/example/macronews/service/forecast/NewsAggregationService.java:98-146,152-264`?먯꽌 `NewsEventRepository.findByStatus(NewsStatus.ANALYZED)`濡??댁뒪 ?꾨낫瑜??쎄퀬, `ExternalApiUtils.callAPI(...)`濡?OpenAI???붿껌?섎ŉ, `MarketDataFacade`濡??쒖옣 而⑦뀓?ㅽ듃瑜?遺숈엯?덈떎.
- `/news`??吏곸젒 ?뚮뜑留곷릺??酉곕뒗 `news/list`?대ŉ, ?곸꽭 寃쎈줈??`news/detail`?낅땲?? ?곸꽭 寃쎈줈??`NewsController.detail(...)`?먯꽌 `NewsQueryService.getNewsDetail(id)`瑜?議고쉶????Thymeleaf 紐⑤뜽??梨꾩썎?덈떎.
- provider/write-side 吏??寃쎈줈??`src/main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java:33-83` -> `NewsIngestionService.ingestTopHeadlines(...)` -> `NewsIngestionServiceImpl.loadScheduledHeadlineFeed(...)` -> `NewsSourceProviderSelector.fetchTopHeadlines(...)` -> 媛쒕퀎 provider(`NaverNewsSourceProvider`, `NewsApiServiceImpl`, `GNewsSourceProvider`) -> `NewsEventRepository.save(...)` ?쒖꽌?낅땲??
- 利? `/news` 由ъ뒪???먯껜??repository ?쎄린 寃쎈줈?닿퀬, provider 怨꾩링? ingestion 寃쎈줈瑜??듯빐 ??λ맂 ?댁뒪 怨듦툒???대떦?⑸땲??

## 4. Fail-open and Fallback Verification
- `/news` fail-open behavior: 遺遺꾩쟻?쇰줈 ?뺤씤?? `NewsController.list(...)`??`safeGetRecentNews(...)`, `safeGetMarketSignalOverview(...)`, `safeGetCurrentForecastSnapshot()`, `safeResolveFeaturedSummarySelection()`?먯꽌 ?덉쇅瑜??≪닔?⑸땲?? ?ㅻ쭔 `NewsController.detail(...)`? ?숈씪???덉쟾 ?섑띁媛 ?녾퀬, public detail route ?꾩껜瑜?fail-open?쇰줈 蹂댁옣?섏????딆뒿?덈떎.
- provider fallback order: 肄붾뱶?먯꽌 ?뺤씤?? `NewsSourceProviderSelector.fetchTopHeadlines(...)`??援?궡 ?곗꽑 ??NAVER瑜?癒쇱?, ?댄썑 foreign fallback???ъ슜?섎ŉ, foreign 洹몃９?먯꽌??`newsapi-global`??`gnews-global`蹂대떎 癒쇱? 泥섎━?⑸땲??
- exception isolation: 遺遺꾩쟻?쇰줈 ?뺤씤?? `NewsSourceProviderSelector.collectCandidates(...)`??provider蹂??덉쇅瑜??↔퀬 怨꾩냽 吏꾪뻾?⑸땲?? `NewsAggregationService.generateCurrentSnapshot()`??OpenAI ?몄텧/?뚯떛 ?ㅽ뙣瑜??≪븘 `Optional.empty()`濡?諛섑솚?⑸땲?? ?ㅻ쭔 `ExternalApiUtils.callAPI()` ?먯껜??`.block()` 湲곕컲?대씪 ?μ떆媛?吏?곗뿉 ???寃⑸━媛 蹂댁씠吏 ?딆뒿?덈떎.
- timeout handling: 肄붾뱶?먯꽌 ?뺤씤?섏? ?딆쓬. `src/main/java/com/example/macronews/util/ExternalApiUtils.java:27-57`?먮뒗 timeout ?뚮씪誘명꽣媛 ?녾퀬, `src/main/java/com/example/macronews/config/WebClientConfig.java:12-22`??buffer size留??ㅼ젙?⑸땲??
- whether NAVER failure can break the endpoint: 肄붾뱶??吏곸젒?곸쑝濡쒕뒗 ?꾨땲?? `NaverNewsSourceProvider.fetchTopHeadlines(...)`??鍮꾧뎄??HTTP ?ㅽ뙣/?뚯떛 ?ㅽ뙣 ??鍮?由ъ뒪?몃? 諛섑솚?섍퀬, selector??provider ?덉쇅瑜??≪뒿?덈떎. `/news` 由ъ뒪?몃뒗 洹?寃곌낵媛 鍮꾩뼱??200?쇰줈 ?뚮뜑留곷맗?덈떎.
- whether aggregation degrades gracefully: 遺遺꾩쟻?쇰줈 ?뺤씤?? provider? forecast/featured summary 寃쎈줈??鍮?媛믪쑝濡?degrade?⑸땲?? ?ㅻ쭔 detail route? timeout 遺?щ뒗 graceful degradation??踰붿쐞 諛뽰엯?덈떎.

## 5. Production Blockers
- P1
  - issue: Gradle??`org.springframework.boot:4.0.0-SNAPSHOT` ?뚮윭洹몄씤???댁꽍?섏? 紐삵빐 ?뚯뒪?몄? 寃利앹씠 ?쒖옉?섏? ?딆쓬
  - impact: ?꾩옱 ?곹깭?먯꽌??reliability baseline??CI ?섏??먯꽌 ?ы쁽?????녾퀬, ?대뼡 蹂寃쎈룄 寃利앸릺吏 ?딆쓬
  - evidence: `build.gradle.kts:5,71` 諛?`Plugin [id: 'org.springframework.boot', version: '4.0.0-SNAPSHOT'] was not found`
  - likely fix area: build/plugin management, Spring Boot plugin version pinning ?먮뒗 ??μ냼 ?묎렐??- P1
  - issue: ?몃? HTTP ?몄텧??紐낆떆??timeout???놁쓬
  - impact: NAVER, NewsAPI, GNews, OpenAI, market data 以??섎굹媛 ?먮━嫄곕굹 硫덉텛硫?`/news` 由ъ뒪?몄쓽 forecast/summary 寃쎈줈? ingestion worker媛 ?μ떆媛?釉붾줈?밸맆 ???덉쓬
  - evidence: `ExternalApiUtils.java:27-57`, `WebClientConfig.java:12-22`
  - likely fix area: `WebClient` timeout ?ㅼ젙, `ExternalApiUtils` ?몄텧 ?섑띁, ?꾩슂 ??circuit breaker
- P2
  - issue: public detail route(`/news/{id}`)??list route泥섎읆 ?덉쟾 ?섑띁媛 ?놁쓬
  - impact: repository??view model 怨꾩궛?먯꽌 ?덉긽移?紐삵븳 ?고??꾩씠 ?섎㈃ 500?쇰줈 ?몄텧?????덉쓬
  - evidence: `NewsController.java:158-196`? `GlobalExceptionHandler.java:90-115`
  - likely fix area: `NewsController.detail(...)`??defensive fallback ?먮뒗 detail service 寃쎄퀎???덉쇅 寃⑸━
- P2
  - issue: ?ㅼ젣 `/news` end-to-end reliability瑜?寃利앺븯???듯빀 ?뚯뒪?멸? 遺議깊븿
  - impact: provider fallback, repository read, forecast snapshot, Thymeleaf ?뚮뜑留곸쓽 議고빀 ?뚭?瑜??볦튂湲??ъ?
  - evidence: `PublicNewsAccessIntegrationTest.java:18-45`??`NewsQueryService`? `MarketForecastQueryService`瑜?mock 泥섎━??  - likely fix area: ?ㅼ젣 Mongo/Testcontainers + ?몃? HTTP mock server瑜??ъ슜?섎뒗 `/news` ?듯빀 ?뚯뒪??
## 6. Documentation Mismatch
- README.md???쒗쁽????μ냼 湲곗??쇰줈 ?쇰? ?뚯뒪?몃뒗 ?ㅽ뙣?섍퀬 ?덉뒿?덈떎?앸씪怨??곸?留? ?대쾲 baseline?먯꽌???뚯뒪???ㅽ뙣媛 ?꾨땲??Gradle snapshot ?뚮윭洹몄씤 ?댁꽍 ?ㅽ뙣濡??뚯뒪?멸? ?쒖옉議곗감 ?섏? ?딆븯?듬땲?? 利??뚯뒪??遺덉븞?뺤꽦 ?ㅻ챸???ㅼ젣 ?ㅽ뙣 ?묒긽蹂대떎 醫곴퀬, ?먯씤???ㅻ쫭?덈떎. (`README.md:185`)
- README.md? PROJECT_BRIEF.md??怨듯넻?곸쑝濡?public pages fail gracefully / `/news` must not fail hard瑜?媛뺤“?섏?留? 肄붾뱶??洹?蹂댁옣? `NewsController.list(...)` 以묒떖?닿퀬 `NewsController.detail(...)`源뚯? ?숈씪?섍쾶 ?곸슜?섏????딆뒿?덈떎. (`README.md:225`, `PROJECT_BRIEF.md:152,159`, `NewsController.java:49-86,158-196`)
- README.md???쐄ail-open 諛⑺뼢?쇰줈 蹂닿컯?섏뿀?ㅲ앸뒗 ?쒗쁽? list route? ?쇰? optional dependency????댁꽌??留욎?留? timeout 遺?ъ? detail route???덉쇅 寃⑸━ 遺議깃퉴吏 ?ы븿?섎㈃ ?꾩쭅 ?꾨㈃??蹂댁옣?대씪怨?蹂닿린 ?대졄?듬땲?? (`README.md:185,225`)
- AGENTS.md???꾩옱 肄붾뱶 ?곹깭? 異⑸룎?섎뒗 ?ㅼ쭏??湲곕뒫 ?쒖닠??嫄곗쓽 ?섏? ?딆븘?? ?섎? ?덈뒗 mismatch??蹂댁씠吏 ?딆븯?듬땲??

## 7. Recommended Next Implementation Order
1. 鍮뚮뱶 ?뚮윭洹몄씤 ?댁꽍 臾몄젣瑜?癒쇱? ?댁냼?쒕떎.
   - ??以묒슂?쒓?: ?뚯뒪?몄? 寃利앹씠 ?쒖옉?섏? ?딆쑝硫??대뼡 reliability ?묒뾽???ы쁽 遺덇??ν븯??
   - target files/classes: `build.gradle.kts`, ?꾩슂 ??plugin management ?ㅼ젙
   - required test coverage: `./gradlew test`媛 ?ㅼ젣濡??쒖옉?섎뒗吏, 理쒖냼??`NewsControllerTest`? `NewsSourceProviderSelectorTest`媛 ?ㅽ뻾?섎뒗吏 ?뺤씤
   - risk level: High
2. ?몃? HTTP timeout??紐낆떆?곸쑝濡?異붽??쒕떎.
   - ??以묒슂?쒓?: ?먮┛ provider/OpenAI ?몄텧??`/news`? ingestion worker瑜?釉붾줈?뱁븯??媛?????댁쁺 由ъ뒪?щ떎.
   - target files/classes: `src/main/java/com/example/macronews/util/ExternalApiUtils.java`, `src/main/java/com/example/macronews/config/WebClientConfig.java`, ?꾩슂 ??`application.yaml`
   - required test coverage: timeout/slow-response ??`ExternalApiUtils`媛 鍮좊Ⅴ寃??ㅽ뙣-open?섎뒗吏, `NewsAggregationServiceTest`? provider tests媛 吏???ㅽ뙣瑜??≪닔?섎뒗吏
   - risk level: High
3. `/news/{id}` detail 寃쎈줈?먮룄 fail-open 寃쎄퀎瑜?異붽??쒕떎.
   - ??以묒슂?쒓?: public route ?꾩껜瑜?reliability ??곸씠?쇨퀬 蹂대㈃ list留??덉쟾??寃껋? 遺議깊븯??
   - target files/classes: `src/main/java/com/example/macronews/controller/NewsController.java`
   - required test coverage: `NewsControllerTest`??detail service exception, invalid model data, missing id 耳?댁뒪 異붽?
   - risk level: Medium

4. ?ㅼ젣 `/news` ?듯빀 ?뚯뒪?몃? 蹂닿컯?쒕떎.
   - ??以묒슂?쒓?: ?꾩옱 怨듦컻 ?묎렐 ?뚯뒪?몃뒗 mock 湲곕컲?대씪 provider fallback怨?repository read path瑜??④퍡 寃利앺븯吏 紐삵븳??
   - target files/classes: `src/test/java/com/example/macronews/config/PublicNewsAccessIntegrationTest.java`, ?좉퇋 integration test
   - required test coverage: Mongo-backed list render, provider failure ??empty/partial feed, forecast snapshot absence ??fallback render
   - risk level: Medium

## 8. Step 2 Build Recovery Result
- root cause of plugin resolution failure: `build.gradle.kts`???좎뼵??`org.springframework.boot` ?뚮윭洹몄씤 踰꾩쟾 `4.0.0-SNAPSHOT`???꾩옱 ?ㅼ젙???뚮윭洹몄씤 ??μ냼?먯꽌 ?댁꽍?섏? ?딆븘, Gradle??猷⑦듃 ?꾨줈?앺듃 ?ㅼ젙 ?④퀎?먯꽌 以묐떒?섏뿀??
- exact files changed: `build.gradle.kts`, `docs/reports/reliability-baseline-step1.md`
- what was changed and why: Spring Boot ?뚮윭洹몄씤 踰꾩쟾??`4.0.3`?쇰줈 蹂寃쏀뻽?? Java 25? Spring Boot 4.x+ baseline? ?좎??섎㈃?? snapshot 誘명빐寃곕줈 留됲엳???뚮윭洹몄씤 ?댁꽍留?蹂듦뎄?섍린 ?꾪븳 理쒖냼 蹂寃쎌씠??
- verification commands run:
  - `./gradlew.bat --no-daemon --no-configuration-cache help`
  - `./gradlew.bat --no-daemon --no-configuration-cache test --tests com.example.macronews.controller.NewsControllerTest --tests com.example.macronews.service.forecast.NewsAggregationServiceTest --tests com.example.macronews.service.news.NewsQueryServiceTest --tests com.example.macronews.service.news.NewsApiServiceImplTest --tests com.example.macronews.service.news.source.NewsSourceProviderSelectorTest --tests com.example.macronews.service.news.source.NaverNewsSourceProviderTest --tests com.example.macronews.service.news.source.GNewsSourceProviderTest --tests com.example.macronews.config.PublicNewsAccessIntegrationTest`
- whether tests reached discovery/execution: ?뺤씤?? `help`媛 ?깃났?덇퀬, `test`??`compileJava`, `compileTestJava`, `testClasses`瑜??듦낵?????ㅼ젣 ?뚯뒪???ㅽ뻾 ?④퀎源뚯? 吏꾩엯?덈떎.
- exact passing/failing test classes:
  - passed: `NewsControllerTest`, `NewsAggregationServiceTest`, `NewsQueryServiceTest`, `NewsApiServiceImplTest`, `NewsSourceProviderSelectorTest`, `GNewsSourceProviderTest`, `PublicNewsAccessIntegrationTest`
  - failed: `NaverNewsSourceProviderTest` (9媛??ㅽ뙣)
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
- remaining blockers: build ?ㅼ젙? 蹂듦뎄?섏뿀吏留?`NaverNewsSourceProviderTest`??湲곗〈 ?뚭?/?뺥빀???ㅽ뙣媛 ?⑥븘 ?덉뼱 ?꾩껜 ?뚯뒪???뱀깋 ?곹깭???꾨땲?? ?꾩옱 李⑥닔?먯꽌??`/news` ?숈옉 蹂寃??놁씠 build/test ?ㅽ뻾 媛???곹깭留?蹂듦뎄?덈떎.
