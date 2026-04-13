# 2026-04-13 Step 1 Provider Failure Boundary Recheck

## Scope
- Analysis-only step.
- No application code was changed.
- This note re-confirmed where the ingestion pipeline first collapses to zero usable items.

## Code Path Rechecked
- Scheduler entry and guard rails: [`ScheduledNewsIngestionJob.java`](../main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java#L41)
- Admin-triggered ingestion entry: [`AdminNewsController.java`](../main/java/com/example/macronews/controller/AdminNewsController.java#L212)
- Batch ingestion service: [`NewsIngestionServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java#L112)
- Provider planning and zero-result summary: [`NewsSourceProviderSelector.java`](../main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java#L73)
- Naver parsing and item-level filtering: [`NaverNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java#L164)

## Reconfirmed Boundary
- `ScheduledNewsIngestionJob` only decides whether a scheduled run starts.
- `AdminNewsController` only routes admin-triggered ingestion into the same service path.
- `NewsIngestionServiceImpl` consumes the provider output and applies the final batch ingestion flow; it does not eliminate Naver items first.
- `NewsSourceProviderSelector` aggregates provider outputs and emits the zero-result summary when both freshness buckets end empty.
- The first real collapse point is inside `NaverNewsSourceProvider`, where items are removed by:
  - not-configured guard
  - upstream-empty or rejected responses
  - null or invalid publish dates
  - stale-date filtering
  - relevance filtering
  - missing usable URL or empty title cases

## Validation
- Rechecked the live code path in the five classes above.
- Rechecked the 2026-04-06 real ingestion trace in [`docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`](2026-04-06-admin-auto-ingestion-real-logs.md).
- Confirmed the trace pattern still matches the provider-side collapse described by the code.

## Not Changed
- No freshness thresholds were changed.
- No provider ranking or selector logic was changed.
- No controller contract was changed.
- No tests were added or updated.
- No ops documents were modified.

## Risk
- If the upstream Naver payload continues to be stale or unusable, the selector and service will still observe zero usable items even though their logic is functioning as designed.

## Next Possible Step
- If recovery is still desired, Step 2 should make the smallest safe provider-side change only.
