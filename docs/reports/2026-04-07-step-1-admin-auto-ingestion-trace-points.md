# 2026-04-07 Step 1: Admin Auto Ingestion Trace Points

## Scope Confirmed
- This step is analysis-only.
- No application code was changed.
- The goal was to confirm the exact zero-result trace path and narrow the smallest safe edit set for Step 2.

## Real Trace Path
- Scheduler entry and skip state live in [`ScheduledNewsIngestionJob.java`](../main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java):55-88.
- Admin manual/automatic ingestion entry lives in [`AdminNewsController.java`](../main/java/com/example/macronews/controller/AdminNewsController.java):81-92 and 280-317.
- Batch selection and final freshness filtering live in [`NewsIngestionServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java):337-349.
- Provider planning, provider outcomes, and final candidate counts live in [`NewsSourceProviderSelector.java`](../main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java):55-107 and 163-180.
- Provider-specific failure attribution lives in:
  - [`NaverNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java):155-227
  - [`NewsApiServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsApiServiceImpl.java):86-246
  - [`GNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/GNewsSourceProvider.java):71-160

## Failure Taxonomy Observed
- `scheduler-disabled`
- `auto-ingestion-already-running`
- `news-source-not-configured`
- provider disabled or incomplete configuration
- provider upstream failure or upstream rate limit
- provider returned only stale items
- selector produced zero fresh and zero semi-fresh candidates
- final freshness gate removed every selected item

## Evidence From Logs
- The real log trace shows provider planning, then `EMPTY` outcomes for Naver, NewsAPI, and GNews, followed by `freshCandidates=0 semiFreshCandidates=0` and `selected sourceSummary={}`.
- The same run also shows scheduler skip reasons already present in the scheduler path, but the zero-result admin path still lacks a single final explanation that ties configuration, provider failures, and selector outcome together.
- QA notes separately request `runId`, `started`, and `skipped reason` visibility, which fits the scheduler entry path and should stay distinct from provider diagnostics.

## Minimal Edit List For Step 2
- Add one explicit summary log in `NewsSourceProviderSelector` for the no-results path so the selector explains why both candidate buckets stayed empty.
- Add one reason-bearing log in `NewsIngestionServiceImpl` for the final freshness gate so operators can see whether the zero-result state was caused before or after the gate.
- Add one provider-level reason log per provider-empty case, keeping the message specific to configuration, stale-only input, upstream rejection, or rate limit.
- Keep `AdminNewsController` unchanged unless Step 2 needs to surface an existing service/selector result in the admin log line.

## Not Changed
- No freshness thresholds were changed.
- No retry behavior was changed.
- No API contract or data model was changed.
- No tests were edited in this step.

## Validation Performed
- Cross-checked `docs/ops/2026-04-07/QA_INBOX.md` and `docs/ops/2026-04-07/QA_STRUCTURED.md` against the real log trace in `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`.
- Confirmed the step objective from `docs/ops/2026-04-07/TODAY_STRATEGY.md` matches the observed zero-result path.

## Next Step
- Execute Step 2 only: add the minimal operator-grade diagnostics for the zero-result path without changing ingestion behavior.
