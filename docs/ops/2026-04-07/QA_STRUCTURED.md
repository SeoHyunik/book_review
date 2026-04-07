# QA_STRUCTURED

## Date
2026-04-07

## Normalized Issues

### Item 1. Automatic ingestion returns zero usable items despite configured providers
- category: reliability
- surface: admin automatic ingestion, provider selection, scheduler, Naver News, NewsAPI, GNews
- symptom: the run shows provider planning and execution traces, but ends with `freshCandidates=0`, `semiFreshCandidates=0`, `finalSelection fresh=0 semiFresh=0`, `selected sourceSummary={}`, and `returned=0/analyzed=0`; Naver reports `parsedItems=0` with stale items, NewsAPI hits `429` and is skipped, and GNews returns `400`
- requested change: expose one traced failure path that makes provider configuration state, per-provider failure reasons, and selector output visible so operators can tell whether the zero-result state comes from provider setup, upstream rejection, or freshness filtering
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### Item 2. Decide whether the final freshness gate should remain removed
- category: product-decision
- surface: ingestion selection policy, freshness gate, fallback semantics
- symptom: the notes mention `final freshness gate removed=...`, but the run still finishes at zero usable items, so it is unclear whether the removal is correct or whether a later fallback path should be restored or made explicit
- requested change: decide whether the final freshness gate should stay removed, be restored, or be replaced with clearer selection rules before relying on the current cascade
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes
