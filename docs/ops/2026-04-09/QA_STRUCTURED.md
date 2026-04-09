# QA_STRUCTURED

## Date
2026-04-09

## Normalization Note
- `QA_INBOX.md` is a carry-over reminder rather than a fresh issue source.
- Actionable items were recovered from the 2026-04-08 structured output and daily handoff, then merged to remove overlap.

## Implementation-Ready Items

### Item 1. Admin auto-ingestion zero-result diagnostics visibility
- category: reliability
- surface: admin automatic ingestion, scheduler entry, provider selection, provider-specific failure logging, admin logs/UI
- symptom: zero-result runs still collapse into `parsedItems=0`, provider failures, `freshCandidates=0`, `selected sourceSummary={}`, `returned=0`, and `analyzed=0` without a single operator-readable reason.
- requested change: record `runId`, start/skip reason, provider configured state, provider failure reason, selector final empty reason, and final zero-result cause so logs and the admin view show one traceable outcome.
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### Item 2. Daily ops continuity gate for reusable handoff
- category: process
- surface: QA inbox, QA structured, TODAY_STRATEGY, DAILY_HANDOFF, `.workday-state.json`
- symptom: unresolved work still has to be reconstructed from prior-day artifacts, and the handoff path can end without a reusable state boundary; ops files also show encoding/format corruption risk.
- requested change: add a hard continuity check so QA, strategy, handoff, and workday-state artifacts preserve the same unresolved work state across sessions and flag encoding or format corruption early.
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

## Broader Product Decisions

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
