# TODAY_STRATEGY

## 1. Date
2026-04-09

---

## 2. Strategy Objective
Stabilize the admin zero-result ingestion path by making the outcome traceable for operators without changing ingestion behavior.

In parallel, close the daily continuity gap by making the unresolved work state explicit across QA, strategy, and handoff artifacts.

---

## 3. Current Context Summary
- The zero-result admin ingestion path is already traced across scheduler entry, admin entry, provider selection, and final freshness filtering.
- The current failure mode is not a lack of trace path knowledge; it is the lack of one operator-readable end-state reason.
- The previous day's handoff preserved the same carry-over work, but the session still ended without a reusable continuity boundary.
- `QA_STRUCTURED.md` gives a clear, prioritized plan; `QA_INBOX.md` is only a low-fidelity cross-check and contains raw reminder text plus encoding noise.

---

## 4. Carry-over from Previous Session
- Admin auto-ingestion zero-result diagnostics
  - previous status: partial
  - why it was not completed: the trace path was identified, but no application code was changed in the prior session.
  - still relevant: yes
  - decision today: continue now
- Reusable handoff completion gate / workday-state enforcement
  - previous status: blocked
  - why it was not completed: the prior session ended without a reusable handoff boundary or synchronized workday state.
  - still relevant: yes
  - decision today: continue now
- Final freshness gate policy
  - previous status: deferred
  - why it was not completed: it is still a product decision and not required to unblock diagnostics.
  - still relevant: yes
  - decision today: defer again
- Public page follow-up scope
  - previous status: deferred
  - why it was not completed: it is lower leverage than fixing operator visibility and session continuity.
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-09/QA_STRUCTURED.md`
- `docs/ops/2026-04-09/QA_INBOX.md`
- `docs/ops/2026-04-08/DAILY_HANDOFF.md`
- `docs/reports/2026-04-07-step-1-admin-auto-ingestion-trace-points.md`
- `docs/reports/2026-04-08-step-2-daily-ops-continuity-check.md`
- `docs/reports/2026-04-07-step-2-re-review-and-handoff-check.md`

---

## 6. User-Observed Issues
- symptom: admin auto-ingestion runs still end in zero-result states without a single clear reason for operators
  - where it appears: admin ingestion logs, selector summary, and final freshness path
  - why it matters: triage stays slow because the empty outcome is visible, but its cause is not
- symptom: the session still lacks a reusable boundary that makes the unresolved work state obvious at day close
  - where it appears: QA, strategy, and handoff chain
  - why it matters: the next session must reconstruct context instead of resuming from a clean state

---

## 7. Code / System Findings
- The trace report confirms the zero-result path spans scheduler entry, admin entry, provider selection, and final freshness filtering, so one log line alone cannot explain the outcome.
- Provider-level empty outcomes and selector-level empty outcomes are already distinguishable in the trace; the gap is the final operator-facing summary.
- The previous handoff shows an unresolved continuity gap because the reusable handoff boundary was not closed.
- `QA_INBOX.md` does not materially disagree with `QA_STRUCTURED.md`; it only adds raw reminder text and shows formatting and encoding noise.
- The freshness gate question remains a product decision, not a prerequisite for the diagnostics fix.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: zero-result ingestion runs need one traceable explanation for operators.
  - scope: add reason-bearing logs or summary markers in the admin ingestion path, selector, provider-empty cases, and final freshness gate.
- process / harness
  - why it exists: the workday needs a reusable boundary so unfinished work does not have to be reconstructed manually.
  - scope: enforce continuity checks across QA, strategy, handoff, and any workday-state indicator used by the harness.
- product decision
  - why it exists: the final freshness gate behavior is still unresolved as a policy question.
  - scope: defer or redesign the gate only after the diagnostics path is clearer.
- public follow-up
  - why it exists: there is still an open user-facing follow-up after ingest stability work.
  - scope: define the next public page or UX step without widening today's execution.

---

## 9. Priority Order
1. reliability
2. process / harness
3. product decision
4. public follow-up

---

## 10. Selection Logic
- Carry-over item 1 stays selected because it is still a P1 reliability gap and the smallest safe change surface is known from the trace reports.
- Carry-over item 2 stays selected because the current planning chain still ends without a reusable closure point, which keeps the next session noisy and error-prone.
- The freshness gate policy is explicitly deferred because it is still a product decision and does not block the diagnostics work.
- The public follow-up is deferred because it is lower leverage than fixing operator visibility and session continuity.
- `QA_INBOX.md` did not change the priority order; it only confirmed that no new actionable issue displaced the structured list.

---

## 11. Selected Work for Today
- reliability
  - goal: make zero-result admin ingestion runs explain themselves with one traceable summary and supporting reason-bearing logs.
  - why selected: it is the highest-impact operational gap and has a clear minimal edit surface.
  - why not deferred: continuing to ship zero-result ambiguity will keep triage slow and noisy.
- process / harness
  - goal: enforce a reusable continuity boundary so QA, strategy, and handoff preserve the same unresolved work state.
  - why selected: today still lacks a clean end-of-day state and the next session should not rebuild context from scratch.
  - why not deferred: the issue is already affecting the planning loop, not just future documentation quality.

---

## 12. Step Breakdown

### Step 1. Add operator-readable zero-result diagnostics

**Goal**
- Produce one clear end-state explanation for admin auto-ingestion zero-result runs without changing ingestion behavior.

**Target Area**
- service / provider / controller logging path

**Likely Files**
- `src/main/java/.../AdminNewsController.java`
- `src/main/java/.../NewsIngestionServiceImpl.java`
- `src/main/java/.../NewsSourceProviderSelector.java`
- provider implementations that already emit empty-result outcomes

**Forbidden Scope**
- do not change freshness thresholds
- do not change provider selection behavior
- do not add new public API contracts
- do not refactor unrelated ingestion code

**Validation**
- inspect logs for a run that previously ended in `selected sourceSummary={}` and confirm the final summary now states why it was empty
- run targeted tests only if existing coverage already exercises the same ingestion path

**Expected Output**
- code
- targeted verification notes

### Step 2. Make continuity state explicit for the day boundary

**Goal**
- Ensure QA, strategy, and handoff preserve the same unresolved work state and flag missing or corrupted continuity early.

**Target Area**
- docs / harness / workday-state boundary

**Likely Files**
- `docs/ops/2026-04-09/DAILY_HANDOFF.md`
- `docs/ops/HARNESS_FAILURES.md`
- any existing workday-state file used by the harness

**Forbidden Scope**
- do not broaden scope into product-policy changes
- do not rewrite unrelated ops history
- do not mix in the freshness-gate decision

**Validation**
- confirm the selected carry-over items appear consistently across `QA_STRUCTURED.md`, `TODAY_STRATEGY.md`, and the resulting handoff
- verify the handoff closes the continuity gap without inventing new work

**Expected Output**
- doc
- harness note

---

## 13. Recommended Agent Flow
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

This is the default flow and is sufficient for both selected items.

---

## 14. Codex Execution Notes
- read:
  - `PROJECT_BRIEF.md`
  - `AGENTS.md`
  - `HARNESS_RULES.md`
  - `DEV_LOOP.md`
  - this strategy file
- use:
  - only `docs/ops/2026-04-09/` for daily ops outputs
- must not:
  - create daily docs outside the date folder
  - modify unrelated files
  - mix the selected work with freshness-policy work
- must:
  - execute the two selected steps in order of operational risk
  - validate before committing any future implementation

---

## 15. Risks and Constraints
- The zero-result path spans several components, so the diagnostic change must stay minimal or it will spread too far.
- The continuity gate can drift into harness design if we are not strict about the smallest useful check.
- `QA_INBOX.md` has low fidelity and some encoding noise, so it should remain a cross-check only.
- The handoff/state boundary may already have a separate mechanism, and we should not overwrite it blindly.

---

## 16. Deferrals
- Final freshness gate policy
  - reason: product decision, not required to unblock diagnostics
  - revisit: after the zero-result path is explainable
- Public page follow-up scope
  - reason: lower priority than operational stability
  - revisit: after the reliability and continuity items are closed

---

## 17. Definition of Done for Today
- the zero-result admin ingestion path has a traceable explanation
- continuity between QA, strategy, and handoff is explicitly preserved
- no unrelated files were pulled into the plan
- deferred policy work remains deferred on purpose
- the next session can start from a clear state

---

## 18. Handoff Requirement
At end of work, MUST generate:

`docs/ops/2026-04-09/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
