# TODAY_STRATEGY

## 1. Date
2026-04-07

---

## 2. Strategy Objective

Stabilize admin automatic ingestion diagnostics so the zero-result path can be explained from logs without changing ingestion behavior.
Keep the work limited to observability and leave freshness-policy changes as a separate decision.

---

## 3. Current Context Summary

- The admin automatic ingestion failure is reproducible and now traced to concrete code locations.
- Scheduler entry and skip state, controller entry, provider selection, final freshness filtering, and provider-specific failures are all visible in the current report set.
- The main gap is not behavior uncertainty; it is the lack of one operator-facing explanation that ties the empty result to the correct failure reason.
- No code changes have been made yet.

---

## 4. Carry-over from Previous Session

- Public interaction path follow-up
  - previous status: partial
  - why it was not completed: the session ended after trace-and-review work, with no implementation step started
  - still relevant: yes, but lower priority than today's admin ingestion issue
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
- `docs/reports/2026-04-07-step-1-admin-auto-ingestion-trace-points.md`
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
  - symptom: `QA_INBOX.md` asks for `runId`, `started`, and `skipped reason` visibility
  - where it appears: scheduler entry path
  - why it matters: the same failure investigation needs clear entry and skip tracing, not only provider-level logs

---

## 7. Code / System Findings

- `ScheduledNewsIngestionJob.java` owns scheduler entry and skip state.
- `AdminNewsController.java` owns admin manual and automatic ingestion entry.
- `NewsSourceProviderSelector.java` plans providers and emits the final candidate counts.
- `NewsIngestionServiceImpl.java` applies the final freshness filtering.
- `NaverNewsSourceProvider.java`, `NewsApiServiceImpl.java`, and `GNewsSourceProvider.java` contain the provider-specific failure sites.
- The observed failure taxonomy includes `scheduler-disabled`, `auto-ingestion-already-running`, `news-source-not-configured`, provider disabled or incomplete configuration, provider upstream failure or upstream rate limit, provider returned only stale items, selector produced zero fresh and zero semi-fresh candidates, and final freshness gate removal.
- `QA_INBOX.md` treats scheduler trace visibility as supporting detail, while `QA_STRUCTURED.md` folds it into the main observability issue. I treat those as consistent, not materially conflicting.

---

## 8. Candidate Work Buckets

- reliability / observability
  - why it exists: the current admin ingestion failure is real, reproducible, and not explainable enough from logs
  - scope: expose provider config state, per-provider failure reasons, scheduler entry or skip state, and selector output for the zero-result path

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

- Item 1 is selected because it is the highest-impact, best-evidenced production issue and can be addressed without changing ingestion semantics.
- Item 2 is not selected because it is a policy decision; changing it now would widen scope and risk behavior drift.
- The public UI carry-over items remain relevant, but they are lower priority than today's admin ingestion visibility problem.
- The scheduler trace request in `QA_INBOX.md` does not add a new bucket; it fits inside the same observability work.
- Trade-off: keep the step focused on making the failure path legible, not on altering ingestion behavior.

---

## 11. Selected Work for Today

- reliability / observability
  - goal: emit a single clear explanation for provider-empty outcomes and the final zero-result selector path
  - why selected: operators need attribution now, and the trace report identifies the smallest safe change surface
  - why not deferred: leaving the current logs as-is keeps the admin flow opaque and slows incident triage

---

## 12. Step Breakdown

### Step 1. Add minimal operator-grade diagnostics

**Goal**
- Emit reason-bearing logs for provider-empty outcomes and the final zero-result path

**Target Area**
- service / provider

**Likely Files**
- `NewsSourceProviderSelector`
- `NewsIngestionServiceImpl`
- `NaverNewsSourceProvider`
- `NewsApiServiceImpl`
- `GNewsSourceProvider`
- `AdminNewsController` only if the existing admin log line needs one small context field

**Forbidden Scope**
- no freshness-threshold changes
- no retry behavior changes
- no request-limit changes
- no new data model
- no API contract changes

**Validation**
- Compare the new log output against `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- Confirm the zero-result run now states the provider failure reason, selector empty state, and final outcome

**Expected Output**
- code changes with a focused log-verified trace

### Step 2. Re-review and handoff

**Goal**
- Verify the behavior is unchanged and record carry-over cleanly

**Target Area**
- docs / tests

**Likely Files**
- `docs/ops/2026-04-07/DAILY_HANDOFF.md`
- existing tests only if a low-cost assertion already covers the path

**Forbidden Scope**
- no unrelated cleanup

**Validation**
- Diff review
- Check that the new logs match the traced failure taxonomy

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
- `QA_INBOX.md` and `QA_STRUCTURED.md` are not perfectly aligned, so the implementation should stay focused on the shared core requirement

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
