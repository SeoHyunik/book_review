# QA_STRUCTURED

## Date
2026-04-13

## Normalization Note
- Raw QA notes were normalized into one implementation-ready issue and one broader product decision.
- The Naver collection failure and the request for log accessibility were kept as related but separate items so execution can stay narrow.
- The carry-over context from 2026-04-10 is represented through the selected ingestion issue and the logging decision.

## Implementation-Ready Items

### 1. Naver news collection is currently failing
- category: ingestion
- surface: Naver news collection batch / server-side crawl job
- symptom: 현재 Naver 뉴스 수집이 되지 않아 최신 뉴스가 들어오지 않는다
- requested change: trace the failing collection path, verify the server-side error, and restore news ingestion with the smallest safe fix
- impact: public freshness and downstream interpretation quality degrade when the collection pipeline is empty
- priority: high
- selected today: yes
- carry-over candidate: yes

## Broader Product Decisions

### 1. Batch log output needs an operator-accessible sink
- category: observability / operational workflow
- surface: batch logging and log retrieval for the news collection pipeline
- symptom: 서버가 불안정하면 로그 파악이 어려워 root-cause analysis가 막힌다
- requested change: decide whether batch logs should be written to Admin email, Google Drive, or a dated GitHub directory so operators and Codex can inspect them after `git pull`
- impact: faster diagnosis of future ingestion failures and less dependency on live server access
- priority: medium
- selected today: no
- carry-over candidate: yes
