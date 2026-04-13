# 2026-04-13 Step 1: Failure Boundary Reconfirm

## Scope
- This step is analysis-only.
- No application code was changed.
- The goal was to re-confirm where the ingestion pipeline first collapses to zero usable items.

## Confirmed Execution Path
- Scheduler entry and guard rails live in [`ScheduledNewsIngestionJob.java`](../main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java).
- Admin-triggered ingestion also routes into the same ingestion service from [`AdminNewsController.java`](../main/java/com/example/macronews/controller/AdminNewsController.java).
- The batch ingestion path starts in [`NewsIngestionServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java) and applies a final freshness gate in `loadScheduledHeadlineFeed(...)`.
- Provider planning, provider outcomes, and candidate aggregation are handled by [`NewsSourceProviderSelector.java`](../main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java).
- Naver parsing, relevance filtering, and empty-result reasons are handled by [`NaverNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java).

## Boundary Reconfirmed
- The scheduler and admin controller only decide whether ingestion starts.
- The selector only aggregates provider outputs and records the final zero-result summary.
- The first real failure boundary is inside the Naver provider, where items are removed by stale-date checks, missing publish dates, relevance filtering, or unusable payloads.
- When Naver returns no usable items, the selector later reports `freshCandidates=0` and `semiFreshCandidates=0`, and the ingestion service then sees an empty selection.

## What the Logs and Code Agree On
- The real trace in [`docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`](2026-04-06-admin-auto-ingestion-real-logs.md) matches the code path.
- Naver repeatedly produces only stale or unusable items.
- NewsAPI and GNews can also return empty results in the same run.
- The final service outcome is zero processed items, but that is a downstream effect of upstream provider exhaustion.

## Validation
- Cross-checked the live code path in the five classes above.
- Cross-checked the real trace in [`docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`](2026-04-06-admin-auto-ingestion-real-logs.md).

## Not Changed
- No freshness thresholds were changed.
- No provider ranking or selector logic was changed.
- No controller behavior was changed.
- No tests were edited.
- No ops documents were modified.

## Conclusion
- Step 1 confirms the failure boundary is upstream of the final ingestion summary.
- The next safe step, if recovery is desired, is a minimal provider-side fix rather than selector or controller surgery.
