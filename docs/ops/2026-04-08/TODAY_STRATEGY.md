# TODAY_STRATEGY

## 1. Date
2026-04-08

---

## 2. Strategy Objective
Stabilize admin auto-ingestion diagnostics so the zero-result path can be explained from logs without changing ingestion behavior.
In parallel, tighten daily ops continuity so the next session can recover carry-over state without re-reading stale context.

---

## 3. Current Context Summary
- Admin auto-ingestion still reaches a zero-result state without a single operator-facing explanation that ties together provider config, upstream failure, stale-only input, selector emptiness, and the final freshness gate.
- The 2026-04-07 trace report already narrowed the execution path and the smallest safe edit surface.
- Today's QA structured file contains two implementation-ready items; the QA inbox only repeats the carry-over reminder, so it is a cross-check source rather than a separate planning input.
- The previous handoff left admin diagnostics, final freshness policy, daily ops consistency, and public follow-up open. Today only the two lowest-risk, highest-value items are selected.

---

## 4. Carry-over from Previous Session
- Admin auto-ingestion zero-result diagnostics
  - previous status: partial
  - why it was not completed: trace-path analysis finished first; the actual log improvement had not been applied yet
  - still relevant: yes
  - decision today: continue now

- Daily ops consistency gate
  - previous status: blocked
  - why it was not completed: there was no minimal rule yet to keep QA, strategy, and handoff in the same state chain
  - still relevant: yes
  - decision today: continue now

- Final freshness gate decision
  - previous status: deferred
  - why it was not completed: observability and policy should be separated before any behavior change
  - still relevant: yes
  - decision today: defer again

- Public interaction / public page follow-up
  - previous status: deferred
  - why it was not completed: it is not directly tied to today's ingestion stability goal
  - still relevant: yes
  - decision today: defer again

- Main-page table / chrome polish
  - previous status: deferred
  - why it was not completed: it is not needed to unblock today's P1 stability work
  - still relevant: yes
  - decision today: defer again

- Korean tone and locale cleanup
  - previous status: deferred
  - why it was not completed: it is a separate user-facing text pass, not an ops stability change
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `HARNESS_RULES.md`
- `DEV_LOOP.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-08/QA_STRUCTURED.md`
- `docs/ops/2026-04-08/QA_INBOX.md`
- `docs/ops/2026-04-07/DAILY_HANDOFF.md`
- `docs/reports/2026-04-07-step-1-admin-auto-ingestion-trace-points.md`
- `docs/reports/2026-04-07-step-2-re-review-and-handoff-check.md`
- `docs/reports/2026-04-06-harness-ops-consistency-check.md`
- user instruction to keep the plan small, safe, and Codex-executable

---

## 6. User-Observed Issues
- Admin auto-ingestion returns zero usable items, but the reason is not readable from one log line.
  - symptom: `parsedItems=0`, provider failures, `freshCandidates=0`, `selected sourceSummary={}`, `returned=0`, `analyzed=0`
  - where it appears: admin automatic ingestion flow, provider selector, final freshness filtering
  - why it matters: the same zero-result state can come from config, upstream, stale-only input, or freshness filtering

- Daily ops continuity is weak across sessions.
  - symptom: the next session may need to reconstruct carry-over items from multiple documents
  - where it appears: QA inbox, QA structured, today strategy, daily handoff
  - why it matters: if the state chain is unclear, the next round starts with avoidable context recovery work

- QA inbox is only a cross-check reminder, not a new issue source.
  - symptom: it contains only a short carry-over note
  - where it appears: `docs/ops/2026-04-08/QA_INBOX.md`
  - why it matters: the structured QA file must remain the primary planning input

---

## 7. Code / System Findings
- `ScheduledNewsIngestionJob.java` owns scheduler entry and skip state.
- `AdminNewsController.java` owns admin manual and automatic ingestion entry.
- `NewsSourceProviderSelector.java` owns provider planning and final candidate counts.
- `NewsIngestionServiceImpl.java` owns final freshness filtering.
- `NaverNewsSourceProvider.java`, `NewsApiServiceImpl.java`, and `GNewsSourceProvider.java` contain provider-specific failure sites.
- The 2026-04-07 trace report confirmed that the zero-result path is split across scheduler, controller, provider, selector, and freshness gate.
- `QA_STRUCTURED.md` and `QA_INBOX.md` do not materially conflict. The inbox only repeats the carry-over reminder and does not change the selected work.
- The 2026-04-06 harness ops consistency report showed that checking whether a daily file exists is not enough; carry-over state also needs continuity checks.

---

## 8. Candidate Work Buckets
- reliability / observability
  - why it exists: admin auto-ingestion zero-result states are not explainable enough from the current logs
  - scope: provider config state, provider-empty reason, selector no-results summary, final zero-result cause logging

- ops / harness
  - why it exists: QA, strategy, and handoff can lose carry-over state between sessions
  - scope: daily ops consistency gate, encoding and format hygiene, carry-over continuity check

- product decision
  - why it exists: the final freshness gate is a policy choice, not a logging problem
  - scope: decide whether to remove, relax, or replace the gate

- public UX follow-up
  - why it exists: public page follow-up and chrome polish are still open user-facing improvements
  - scope: public interaction path, table/chrome polish, locale tone cleanup

---

## 9. Priority Order
1. reliability / observability
2. ops / harness
3. product decision
4. public UX follow-up

---

## 10. Selection Logic
- Item 1 is selected because it is P1, the trace report already narrowed the change surface, and it can be addressed without changing ingestion semantics.
- Item 2 is selected because it improves session continuity without changing product behavior.
- The final freshness gate decision is important, but it is a separate policy question and would widen scope if handled today.
- Public follow-up and UI polish remain relevant, but they do not unblock the current stability problem.
- The QA inbox does not introduce a new conflict or new scope, so it does not change the selection.
- Trade-off: keep today's work focused on traceability and continuity instead of behavior changes.

---

## 11. Selected Work for Today
- reliability / observability
  - goal: make the zero-result path readable from logs by separating provider-empty reason, selector empty summary, and final cause
  - why selected: operators need immediate attribution for the current failure mode
  - why not deferred: leaving the logs unchanged keeps triage slow and ambiguous

- ops / harness
  - goal: keep QA, strategy, and handoff aligned on the same carry-over state
  - why selected: the next session should not need to rebuild today's context from scratch
  - why not deferred: continuity problems reduce the value of the code work that follows

---

## 12. Step Breakdown

### Step 1. Add zero-result diagnostics for admin auto-ingestion

**Goal**
- Add minimal logs that connect provider config state, provider failure reason, selector empty summary, and final freshness cause

**Target Area**
- service / provider / controller

**Likely Files**
- `NewsSourceProviderSelector.java`
- `NewsIngestionServiceImpl.java`
- `NaverNewsSourceProvider.java`
- `NewsApiServiceImpl.java`
- `GNewsSourceProvider.java`
- `AdminNewsController.java` only if one small context field is needed

**Forbidden Scope**
- no freshness threshold changes
- no retry behavior changes
- no public API contract changes
- no data model changes

**Validation**
- Compare the new logs against the trace report and confirm the zero-result cause is readable in one pass
- Review the diff to confirm behavior did not change, only observability

**Expected Output**
- focused code change with clearer operator logs

### Step 2. Tighten daily ops continuity checks

**Goal**
- Keep QA, strategy, and handoff aligned on the same carry-over state with a minimal continuity check

**Target Area**
- docs / harness

**Likely Files**
- `docs/ops/2026-04-08/TODAY_STRATEGY.md`
- `docs/ops/2026-04-08/DAILY_HANDOFF.md`
- `docs/ops/HARNESS_FAILURES.md` if an enforcement note is required
- a harness checklist or validation helper only if one already exists and the change stays small

**Forbidden Scope**
- no code behavior changes
- no broad documentation rewrite
- no unrelated cleanup

**Validation**
- Check that QA structured, strategy, and handoff keep the same carry-over items
- Confirm no new encoding or format corruption is introduced

**Expected Output**
- updated daily ops continuity record and a clearer next-session state

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
  - `docs/ops/2026-04-08/` folder only

- must not:
  - create docs outside the ops/date folder
  - modify unrelated files
  - mix multiple steps

- must:
  - execute step by step
  - validate before commit

---

## 15. Risks and Constraints
- scope drift into freshness-policy changes
- logging noise if diagnostics become too broad
- daily ops continuity work could become a documentation rewrite if it is not kept small
- encoding hygiene must not reintroduce mojibake or format corruption

---

## 16. Deferrals
- Final freshness gate decision
  - reason: it needs a policy call before any behavior change
  - when to revisit: after the observability fix is reviewed

- Public interaction / public page follow-up
  - reason: lower priority than today's P1 and harness continuity work
  - when to revisit: when the ingestion path and daily ops continuity are stable

- Main-page table / chrome polish
  - reason: it is not needed to unblock today's work
  - when to revisit: after the stability work is closed out

- Korean tone and locale cleanup
  - reason: it is a separate text pass, not an ops stability change
  - when to revisit: when there is a dedicated UI or content pass

---

## 17. Definition of Done for Today
- The admin auto-ingestion zero-result path is explainable from logs
- Daily ops carry-over state is stable enough for the next session
- No unrelated files are changed
- Policy-only work remains explicitly deferred
- The next session can continue without reconstructing today from scratch

---

## 18. Handoff Requirement
At end of work, MUST generate:

`docs/ops/2026-04-08/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
