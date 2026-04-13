# 2026-04-13 Step 1: Failure Path Reconfirm

## Scope
- This step is analysis-only.
- No application code was changed.
- The goal was to re-confirm the real failure path that leads to zero returned items in the news ingestion pipeline.

## Trace Path Confirmed
- Scheduler entry and gatekeeping live in [`ScheduledNewsIngestionJob.java`](../main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java):41-93.
- Admin-triggered ingestion also calls the same ingestion service from [`AdminNewsController.java`](../main/java/com/example/macronews/controller/AdminNewsController.java):212 and 298-317.
- The batch ingestion path starts in [`NewsIngestionServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java):112-121 and applies the final freshness gate in `loadScheduledHeadlineFeed`:336-361.
- Provider planning, provider outcomes, and final candidate counts are handled by [`NewsSourceProviderSelector.java`](../main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java):55-119 and 178-214.
- Naver-specific filtering and empty-result reasons are handled by [`NaverNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java):164-343.

## What the Current Code Path Shows
- The scheduler can skip early for scheduler-disabled, already-running, or missing source configuration, but the reported zero-result case is not caused by those early guards.
- `NewsSourceProviderSelector.fetchTopHeadlines(...)` first asks the preferred provider for fresh items, then falls back to foreign providers, then retries semi-fresh selection if needed.
- When the providers return no usable items, the selector logs `freshCandidates=0 semiFreshCandidates=0` and returns an empty selection.
- `NewsIngestionServiceImpl.loadScheduledHeadlineFeed(...)` then logs `selected sourceSummary={}` and `batch completed requested=... processed=0 asyncSubmitted=0`.
- The real logs in [`docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`](2026-04-06-admin-auto-ingestion-real-logs.md) match that path: Naver returns only stale or unusable items, NewsAPI and GNews both return empty, selector candidates stay at zero, and the ingestion service processes zero items.

## Failure Taxonomy Reconfirmed
- Scheduler disabled
- Scheduler already running
- No configured news source
- Provider not configured
- Provider upstream rejection or rate limit
- Provider returned only stale items
- Provider returned no usable items after parsing and relevance filtering
- Selector produced no fresh or semi-fresh candidates
- Final freshness gate kept no items because the selection was already empty

## Validation
- Cross-checked the live code path in the five classes above.
- Cross-checked the real trace in [`docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`](2026-04-06-admin-auto-ingestion-real-logs.md).
- Cross-checked the current QA inputs in [`docs/ops/2026-04-13/QA_INBOX.md`](../ops/2026-04-13/QA_INBOX.md) and [`docs/ops/2026-04-13/QA_STRUCTURED.md`](../ops/2026-04-13/QA_STRUCTURED.md).

## Not Changed
- No freshness thresholds were changed.
- No provider ranking or selector logic was changed.
- No controller behavior was changed.
- No tests were edited.
- No ops documents were modified.

## Conclusion
- The failure path is upstream of the final service summary: Naver parsing/filtering and cross-provider selection both converge to an empty candidate set.
- Step 2 should only add the smallest safe recovery change if the goal is to restore usable Naver ingestion.
