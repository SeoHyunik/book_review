# QA_STRUCTURED

## Date
2026-04-08

## Normalization Note
- `QA_INBOX.md` contains one actionable directive: review the unresolved work from 2026-04-07.
- This structured list carries forward the unresolved items and separates implementation-ready work from deferred decisions.

## Implementation-Ready Items

### Item 1. Admin auto ingestion zero-result diagnostics visibility
- category: reliability
- surface: admin automatic ingestion, scheduler entry, provider selection, provider-specific failure logging, admin logs/UI
- symptom: the execution path still collapses into `parsedItems=0`, provider failures, `freshCandidates=0`, `selected sourceSummary={}`, `returned=0`, and `analyzed=0` without a fast operator-readable explanation of whether the failure came from provider config, upstream rejection, or freshness filtering.
- requested change: log `runId`, start/skip reason, provider configured state, provider failure reason, selector final empty reason, and the final zero-result cause so the admin view and logs show a single traceable outcome.
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### Item 2. Daily ops consistency gate
- category: process
- surface: QA inbox, QA structured, TODAY_STRATEGY, DAILY_HANDOFF, harness review context
- symptom: unresolved work must be recovered from prior-day artifacts, but the current handoff path still depends on manual recovery and older ops files show mojibake/format corruption risk.
- requested change: strengthen the daily ops consistency check so QA, strategy, and handoff artifacts preserve the same unresolved work state across sessions and flag encoding or format corruption early.
- impact: medium
- priority: P1
- selected today: yes
- carry-over candidate: yes

## Deferred Decisions

### Item 3. Final freshness gate policy
- category: product-decision
- surface: ingestion selection policy, freshness gate, fallback selection
- symptom: logs still show `final freshness gate removed=...`, and the system can still end in zero usable items even when earlier provider stages returned partial results.
- requested change: decide whether the final freshness gate should be removed, relaxed, or replaced by a clearer selection rule before the zero-result cascade is treated as expected behavior.
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes

### Item 4. Public page follow-up scope
- category: product-decision
- surface: public page reliability, public interaction path, homepage/news follow-up
- symptom: admin ingestion work is clearer now, but the public-facing follow-up remains deferred and the next UX or surface-level step is still undecided.
- requested change: define the next public-page follow-up scope after ingestion diagnostics are stabilized.
- impact: medium
- priority: P3
- selected today: no
- carry-over candidate: yes
