# QA_STRUCTURED

## Date
2026-04-10

## Normalization Note
- Raw QA notes were normalized into one implementation-ready issue and one broader product decision.
- Related ingestion symptoms were merged into a single issue about daytime Naver collection coverage.
- External dependency discovery was separated so it can be scheduled as follow-up work instead of being treated as an immediate code fix.

## Implementation-Ready Items

### 1. Naver news is not being collected during daytime
- category: ingestion
- surface: news collection pipeline / Naver crawler
- symptom: 주간(낮) 네이버 뉴스가 수집되고 있지 않음
- requested change: widen the keyword filters first so collection resumes, then narrow the collected set to KOSPI- and KOSDAQ-related articles
- impact: public news freshness and downstream interpretation quality are degraded when the daytime feed is empty
- priority: high
- selected today: yes
- carry-over candidate: yes

## Broader Product Decisions

### 1. Required index-related API keys and source dependencies need to be inventoried
- category: dependency / product decision
- surface: external market-data integration
- symptom: developers do not yet have a clear, organized list of index-related API keys and external inputs they must obtain directly
- requested change: research and document the index-related API keys and other inputs that the development team must source manually
- impact: implementation can stall if external market-data access requirements are not known early
- priority: medium
- selected today: no
- carry-over candidate: yes
