# TODAY_STRATEGY

## 1. Date
2026-04-07

---

## 2. Strategy Objective

Stabilize admin automatic ingestion diagnostics so the zero-result path can be explained from logs without changing ingestion behavior.
Keep the work limited to observability and leave freshness-policy changes for a separate decision.

---

## 3. Current Context Summary

- Public UI follow-up from 2026-04-06 is still open, but it is not the most urgent issue today.
- Today's QA points to a production-relevant admin ingestion failure: providers are configured, yet the run ends with zero usable items.
- The traced run shows Naver returning only stale items, NewsAPI hitting `429` and skipping follow-up calls, and GNews returning `400`.
- The operator-visible problem is not only the empty result; it is that the failure path is not sufficiently attributable from the current logs.

---

## 4. Carry-over from Previous Session

- Public interaction path follow-up
  - previous status: partial
  - why it was not completed: the session ended after trace-and-review work, with no implementation step started
  - still relevant: yes, but lower priority than today's P1 ingestion issue
  - decision today: defer again

- Main-page table and visible chrome polish
  - previous status: deferred
  - why it was not completed: no implementation was selected
  - still relevant: yes
  - decision today: defer again

- Korean tone and locale cleanup
  - previous status: deferred
  - why it was not completed: no bounded localization pass was selected
  - still relevant: yes
  - decision today: defer again

- Partial update pilot for public pages
  - previous status: deferred
  - why it was not completed: it would require broader controller/template contract work
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning

- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `HARNESS_RULES.md`
- `DEV_LOOP.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-07/QA_STRUCTURED.md`
- `docs/ops/2026-04-07/QA_INBOX.md`
- `docs/ops/2026-04-06/DAILY_HANDOFF.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- user instruction for a small, safe, Codex-executable plan

---

## 6. User-Observed Issues

- Zero usable items from automatic ingestion despite configured providers
  - symptom: the run completes with `requested=10`, `returned=0`, and `analyzed=0`
  - where it appears: admin automatic ingestion flow
  - why it matters: the admin path looks healthy until the final empty result, so operators cannot tell whether the fault is upstream, provider-specific, or selection policy

- Missing operator-grade failure attribution
  - symptom: logs show provider empties, but not a single traced explanation of the final zero-result decision path
  - where it appears: provider selector and provider implementations
  - why it matters: the same empty outcome can come from provider config, upstream rejection, or freshness filtering, and those cases need to be distinguishable

- Scheduler visibility is not explicit enough in the inbox notes
  - symptom: QA_INBOX asks for `runId`, `started`, and `skipped reason` visibility, but the structured issue does not separate it
  - where it appears: scheduler entry path
  - why it matters: the same failure investigation needs clear entry and skip tracing, not only provider-level logs

---

## 7. Code / System Findings

- The traced run ends with provider outcomes of `EMPTY` and a final `selected sourceSummary={}` before the batch completes at zero processed items.
- Naver spends work on queries but still merges to `usableItems=0` because every sampled item is stale.
- NewsAPI hits `429` and then suppresses follow-up calls in the same cycle, which makes the empty return look identical to a normal empty provider unless the reason is surfaced.
- GNews returns `400` and is also reduced to an empty provider outcome.
- QA_INBOX contains an additional scheduler-trace request (`runId`, `started`, `skipped reason`) that is not isolated as a separate structured issue; I treat it as supporting detail for Item 1, not a separate work item.
- The final freshness gate question remains unresolved as a product decision, so changing selection policy in the same step would be too risky.

---

## 8. Candidate Work Buckets

- reliability / observability
  - why it exists: the current admin ingestion failure is real, reproducible, and not explainable enough from logs
  - scope: expose provider config state, per-provider failure reasons, scheduler entry/skip state, and selector output for the zero-result path

- product decision
  - why it exists: the final freshness gate behavior is unclear and should not be changed implicitly
  - scope: decide whether the final freshness gate should remain removed or be restored before any policy change

- docs alignment and handoff
  - why it exists: the next session needs a clean record of what was done and what remains deferred
  - scope: update the daily handoff after the implementation result is known

---

## 9. Priority Order

1. reliability / observability
2. product decision
3. docs alignment and handoff

---

## 10. Selection Logic

- Item 1 is selected because it is P1, production-facing, and already has concrete evidence from the log trace.
- Item 2 is not selected because it is a product-policy decision, not an observability gap, and changing it without explicit agreement would expand scope.
- The public UI carry-over items from 2026-04-06 are deferred again because today's QA reveals a higher-impact admin ingestion issue.
- The QA inbox adds scheduler trace visibility as supporting detail, but it does not justify a separate bucket today.
- Trade-off: keep the step focused on making the failure path legible, not on altering ingestion behavior.

---

## 11. Selected Work for Today

- bucket name: reliability / observability
  - goal: expose the zero-result ingestion path clearly enough that operators can see provider config state, per-provider failure reasons, scheduler entry and skip state, and selector output
  - why selected: this is the highest-impact, best-evidenced issue and can be fixed with a minimal, safe change surface
  - why not deferred: the current logs are insufficient for root-cause attribution, so postponing this keeps the admin flow opaque

---

## 12. Step Breakdown

### Step 1. Confirm trace points and failure taxonomy

**Goal**
- Map the exact zero-result path from scheduler entry through provider selection and identify the smallest log sites that need to speak for themselves

**Target Area**
- service / provider / controller

**Likely Files**
- `NewsSourceProviderSelector`
- `NewsIngestionServiceImpl`
- `AdminNewsController`
- `NaverNewsSourceProvider`
- `NewsApiServiceImpl`
- `GNewsSourceProvider`

**Forbidden Scope**
- no freshness-threshold changes
- no provider-retry changes
- no API contract changes
- no new data model

**Validation**
- Compare the planned trace points against the real logs in `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- Cross-check against the day-scoped QA notes

**Expected Output**
- a minimal implementation plan and targeted edit list

### Step 2. Add minimal operator-grade diagnostics

**Goal**
- Emit one traced explanation for each provider-empty outcome and one final selector summary that makes the zero-result path attributable

**Target Area**
- service / provider / controller

**Likely Files**
- `NewsSourceProviderSelector`
- `NewsIngestionServiceImpl`
- `AdminNewsController`
- `NaverNewsSourceProvider`
- `NewsApiServiceImpl`
- `GNewsSourceProvider`

**Forbidden Scope**
- do not change selection semantics
- do not change freshness logic
- do not change request limits
- do not change fallback behavior

**Validation**
- Verify the new logs show provider config state, upstream failure reason, and selector final outcome on the zero-result run

**Expected Output**
- code changes plus a focused log-verified trace

### Step 3. Re-review and handoff

**Goal**
- Verify the change is minimal, the behavior is unchanged, and the remaining freshness-gate decision is still explicitly deferred

**Target Area**
- docs / tests

**Likely Files**
- relevant tests only if an existing test can assert the new logging path
- otherwise no test expansion

**Forbidden Scope**
- no unrelated cleanup

**Validation**
- Targeted review of the diff
- Fresh daily handoff

**Expected Output**
- review notes and `docs/ops/2026-04-07/DAILY_HANDOFF.md`

---

## 13. Recommended Agent Flow

Default:
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

---

## 14. Codex Execution Notes

- read:
  - `PROJECT_BRIEF.md`
  - `AGENTS.md`
  - `HARNESS_RULES.md`
  - `DEV_LOOP.md`
  - this strategy file

- use:
  - `docs/ops/2026-04-07/` folder only

- must not:
  - create docs outside the ops/date folder
  - modify unrelated files
  - mix multiple steps

- must:
  - execute step-by-step
  - validate before commit

---

## 15. Risks and Constraints

- Scope drift into freshness-policy changes or provider selection redesign
- Scheduler trace may require one extra log site outside the provider selector
- Logging changes can become noisy if too much context is added
- QA_INBOX and QA_STRUCTURED are not perfectly aligned, so the implementation should stay focused on the shared core requirement

---

## 16. Deferrals

- Final freshness gate decision
  - reason: it needs product judgment before any behavior change
  - revisit: after the observability fix is reviewed

- Public UI carry-over items from 2026-04-06
  - reason: lower priority than today's P1 ingestion issue
  - revisit: when the admin flow is stable again

---

## 17. Definition of Done for Today

- The zero-result path is explainable from logs without guesswork
- No unrelated files changed
- The freshness-policy question remains explicitly deferred
- The next session can continue from a clear handoff

---

## 18. Handoff Requirement

At end of work, generate:

`docs/ops/2026-04-07/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
