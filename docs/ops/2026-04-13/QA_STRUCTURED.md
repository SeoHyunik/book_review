# QA_STRUCTURED

## Date
2026-04-13

## Normalization Note
- The raw QA inbox was normalized into one implementation-ready ingestion issue and one broader operational decision about log access.
- The carry-over note from 2026-04-10 was treated as context, not as a separate actionable item.
- The logging request was merged into a single operator-accessibility decision so the scope stays focused and safe.

## Implementation-Ready Items

### 1. Naver news collection is failing and needs restoration
- category: ingestion
- surface: Naver news collection batch job and server-side crawl path
- symptom: news collection is not completing reliably, and server log inspection is difficult because the server is unstable
- requested change: trace the failing collection path, identify the server-side failure, and restore the news ingestion flow with the smallest safe fix
- impact: public freshness drops, downstream interpretation quality degrades, and the daily market narrative becomes stale
- priority: high
- selected today: yes
- carry-over candidate: yes

## Broader Product Decisions

### 1. Batch logs need an operator-accessible destination
- category: observability / operational workflow
- surface: batch logging for the news collection pipeline and post-run log retrieval
- symptom: direct server log inspection is unreliable when the server is unstable, which slows diagnosis
- requested change: decide whether batch logs should be delivered to Admin email, Google Drive, or a dated project GitHub directory so operators and Codex can inspect them after server updates and `git pull`
- impact: faster diagnosis of future ingestion failures, less dependence on live server access, and better rollback/debug visibility
- priority: medium
- selected today: no
- carry-over candidate: yes
