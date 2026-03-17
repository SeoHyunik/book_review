# Macro News

### 거시경제 뉴스 해석 서비스

> 거시경제 뉴스를 수집·조회하고, 해석 파이프라인으로 확장하기 위한 Spring Boot + Thymeleaf 모놀리스입니다.

## 현재 상태

- Step 1 완료: 기존 독후감/리뷰 도메인 제거
- Step 2 완료: 패키지/애플리케이션 아이덴티티를 `macro_news`로 전환
- 현재 유지 범위: 인증, 보안, 공통 유틸, 로깅, 예외 처리, 공통 레이아웃

## 기술 스택

- Java
- Spring Boot
- Spring Security
- Spring Data MongoDB
- Thymeleaf
- Gradle

## 실행

```bash
./gradlew bootRun
```

기본 포트: `8082`

## Runtime configuration

- Runtime secrets and provider settings now come from environment variables: `OPENAI_API_KEY`, `OPENAI_API_URL`, `OPENAI_MODEL`, `OPENAI_MAX_TOKENS`, `OPENAI_TEMPERATURE`, `NEWS_API_KEY`, `JASYPT_PASSWORD`
- Explicit non-dev/test admin bootstrap uses: `APP_ADMIN_ALLOWED_USERNAMES`, `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_PASSWORD`, `APP_BOOTSTRAP_ADMIN_EMAIL`
- Scheduled ingestion is disabled by default and can be enabled with: `APP_INGESTION_SCHEDULER_ENABLED`, `APP_INGESTION_SCHEDULER_CRON`, `APP_INGESTION_SCHEDULER_PAGE_SIZE`
- Keep-alive remains statically gated by `APP_KEEP_ALIVE_ENABLED` and `APP_KEEP_ALIVE_TARGET_URL`, but the admin auto-ingestion page can now turn the runtime keep-alive switch on or off while the app is running
- Email notification remains statically gated by `APP_NOTIFICATION_EMAIL_ENABLED`, `APP_NOTIFICATION_EMAIL_RECIPIENT`, and Spring mail sender configuration, but the admin auto-ingestion page can now turn the runtime email switch on or off while the app is running
- Keep-alive and email runtime toggle state is in-memory only and resets on restart or redeploy
- Automatic headline ingestion switches between the domestic and foreign feed priority windows using: `APP_INGESTION_DOMESTIC_START_HOUR`, `APP_INGESTION_DOMESTIC_END_HOUR` (defaults: `5` to `22`, Asia/Seoul)
- News source providers can be toggled independently: `APP_NEWS_GLOBAL_ENABLED`, `APP_NEWS_NAVER_ENABLED`, with NAVER credentials/config from `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `APP_NEWS_NAVER_QUERIES`, `APP_NEWS_NAVER_DISPLAY`, `APP_NEWS_NAVER_START`
- Domestic NAVER news requires `APP_NEWS_NAVER_ENABLED=true`, `NAVER_CLIENT_ID`, and `NAVER_CLIENT_SECRET`; `APP_NEWS_NAVER_QUERIES` is strongly recommended for production tuning
- If `APP_NEWS_NAVER_QUERIES` is blank, NAVER domestic news now falls back to the built-in safe query set: `코스피`, `코스닥`, `환율`, `금리`, `유가`, `반도체`, `연준`
- Foreign/global NewsAPI ingestion now prefers the recent keyword search first and uses `top-headlines` only as fallback when recent results are insufficient
- Tune the recent foreign/global NewsAPI query with `NEWS_API_RECENT_QUERY` (`news.api.recent-query`)
- Tune macro relevance filtering with `NEWS_API_FILTER_KEYWORDS` (`news.api.filter-keywords`), which matches configured keywords against article title + description before items are accepted
- The main featured card now prefers a deterministic recent-market aggregation built from analyzed news, controlled by `APP_FEATURED_MARKET_SUMMARY_ENABLED`, `APP_FEATURED_MARKET_SUMMARY_WINDOW_HOURS`, `APP_FEATURED_MARKET_SUMMARY_MAX_ITEMS`, and `APP_FEATURED_MARKET_SUMMARY_MIN_ITEMS`
- This featured aggregation step is rule-based only for now; if there are not enough recent analyzed items, the homepage safely falls back to the existing market forecast snapshot or featured news behavior
- Optional market-data providers are scaffolded behind feature flags: `APP_MARKET_FX_ENABLED` + `EXCHANGE_RATE_API_KEY`, `APP_MARKET_GOLD_ENABLED` + `METALPRICE_API_KEY`, `APP_MARKET_OIL_ENABLED` + `OILPRICE_API_KEY`, and future index config via `APP_MARKET_INDEX_PROVIDER`, `APP_MARKET_INDEX_ENABLED`, `TWELVEDATA_API_KEY`
- Local and production environments should provide these explicitly instead of relying on repository-stored values
- Default admin seeding is intended only for `dev` and `test` profiles
- Production-like admin creation is explicit only and requires allowlist membership
- Scheduled ingestion runs only when explicitly enabled and skips overlapping local runs
- Production must use explicitly provisioned users and configuration
