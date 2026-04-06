# 2026-04-06 관리자 자동 수집 기능 점검

## 결론
- 자동 수집이 “아예 시작되지 않는” 가장 유력한 원인은 스케줄러 자체보다 `NewsSourceProviderSelector.isConfigured()`가 false가 되는 구성 상태입니다.
- 소스 기준으로는 자동 수집이 다음 순서로 막힐 수 있습니다.
  1. `AutoIngestionControlService`에서 스케줄러 runtime toggle이 꺼짐
  2. `NewsSourceProviderSelector`에서 구성된 provider가 하나도 없음
  3. provider는 구성되어도 freshness gate에서 모두 제거됨
- 현재 코드만 보면 2번이 가장 강한 후보입니다.

## 실제 실행 경로
- `ScheduledNewsIngestionJob.ingestTopHeadlines()`가 `@Scheduled`로 실행됩니다.
- 먼저 `autoIngestionControlService.isSchedulerEnabled()`를 확인합니다.
- 그 다음 `newsSourceProviderSelector.isConfigured()`가 false면 `news-source-not-configured`로 바로 종료합니다.
- 그 검사를 통과해야만 `autoIngestionControlService.beginScheduledRun(...)`와 `newsIngestionService.ingestTopHeadlines(...)`가 실행됩니다.

## 1차 차단 지점
### `AutoIngestionControlService`
- `AutoIngestionControlService`는 `@Value("${app.ingestion.scheduler.enabled:false}")`로 초기 상태를 정합니다.
- `application.yaml`에는 `app.ingestion.scheduler.enabled: true`가 들어 있어 기본 설정상은 켜지는 구조입니다.
- 다만 환경변수나 다른 프로파일이 이를 덮어쓰면 scheduler가 꺼진 상태가 됩니다.
- 이 경우 `ScheduledNewsIngestionJob`는 아무 수집도 시작하지 않습니다.

## 2차 차단 지점
### `NewsSourceProviderSelector`
- selector는 `provider.supports(priority)`와 `provider.isConfigured()`를 모두 통과한 provider만 사용합니다.
- 현재 등록된 provider는 `NewsApiServiceImpl`, `NaverNewsSourceProvider`, `GNewsSourceProvider`입니다.
- 각 provider의 `isConfigured()` 조건은 아래와 같습니다.
  - `NewsApiServiceImpl`: `enabled && StringUtils.hasText(apiKey)`
  - `NaverNewsSourceProvider`: `enabled && hasClientId() && hasClientSecret()`
  - `GNewsSourceProvider`: `enabled && StringUtils.hasText(apiKey)`
- `application.yaml`에서 이 값들은 모두 환경변수 기반이며 기본값은 비어 있습니다.
- 따라서 로컬 실행 환경에 `NEWS_API_KEY`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `APP_NEWS_GNEWS_API_KEY`가 없으면 provider가 전부 미구성 상태가 됩니다.
- 그 결과 `NewsSourceProviderSelector.isConfigured()`가 false가 되고, scheduler는 `news-source-not-configured`로 종료합니다.

## 3차 차단 지점
### `NewsIngestionServiceImpl`
- provider에서 항목을 받아도 `loadScheduledHeadlineFeed()`가 한 번 더 freshness gate를 적용합니다.
- `isFreshEnoughForBatch()`는 provider가 돌려준 항목 중 게시 시각이 허용 기간 밖이면 전부 제거합니다.
- 이 추가 gate 때문에 provider가 응답을 줘도 최종 `ingested` 결과가 0건이 될 수 있습니다.
- 다만 이건 “수집이 아예 안 된다”기보다는 “실행은 됐지만 결과가 비었다”에 더 가깝습니다.

## 부가 관찰
- `ScheduledNewsIngestionConfigurationLogger`는 enabled / disabled만 로그로 남기고, 왜 막혔는지는 자세히 설명하지 않습니다.
- `NaverNewsSourceProvider` 안에는 `isRelevantForMacroNews()`가 정의돼 있지만, 현재 코드에서는 실제 필터링에 사용되지 않습니다.
- 따라서 현재 수집 실패는 relevance 필터보다 config gate 쪽이 훨씬 유력합니다.

## 확인해야 할 항목
- 애플리케이션 로그에 아래 메시지가 있는지 확인해야 합니다.
  - `[SCHEDULER] automatic ingestion is disabled`
  - `[SCHEDULER] runId=... skipped reason=news-source-not-configured`
- 실행 환경에 아래 값이 실제로 주입되는지 확인해야 합니다.
  - `NEWS_API_KEY`
  - `NAVER_CLIENT_ID`
  - `NAVER_CLIENT_SECRET`
  - `APP_NEWS_GNEWS_API_KEY`
- 자동 수집이 기대대로 돌아가려면 최소 한 provider가 configured 상태여야 합니다.

## 권장 다음 조치
- 로컬/배포 환경에서 실제로 어떤 provider가 configured 상태인지 로그로 즉시 드러내는 진단 로그를 추가한다.
- admin auto page에 `newsApiConfigured`만이 아니라 provider별 configured 상태를 노출한다.
- `NewsSourceProviderSelector.isConfigured()`가 false일 때 어떤 키가 빠졌는지까지 설명하는 상태 DTO를 추가한다.
