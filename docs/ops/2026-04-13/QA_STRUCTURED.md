# QA_STRUCTURED

## Date
2026-04-13

## Normalization Note
- The raw QA inbox contains one clear implementation issue and one related but broader operational decision.
- The log-access request is kept separate from the collection failure so the implementation scope stays minimal and safe.
- The 2026-04-10 carry-over is reflected by marking the ingestion issue as a carry-over candidate.

## Implementation-Ready Items

### 1. Naver news collection is failing and must be restored
- category: ingestion
- surface: Naver news collection batch job and server-side crawl path
- symptom: Naver news collection is not currently completing, and the operator cannot confirm the server-side failure state reliably
- requested change: trace the failing collection path, verify the server error, and restore news ingestion with the smallest safe fix
- impact: public freshness drops, downstream interpretation quality weakens, and the daily market narrative becomes stale
- priority: high
- selected today: yes
- carry-over candidate: yes

## Broader Product Decisions

### 1. Batch logs need an operator-accessible destination
- category: observability / operational workflow
- surface: batch logging for the news collection pipeline and post-run log retrieval
- symptom: the server is unstable enough that checking logs directly is difficult, which blocks diagnosis of the collection issue
- requested change: decide whether batch logs should be written to Admin email, Google Drive, or a dated project GitHub directory so operators and Codex can inspect them after server updates and `git pull`
- impact: faster diagnosis of future ingestion failures, less dependence on live server access, and better rollback/debug visibility
- priority: medium
- selected today: no
- carry-over candidate: yes
