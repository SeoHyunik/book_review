# TODAY_STRATEGY_FORMAT

## 1. Date

YYYY-MM-DD

---

## 2. Strategy Objective

Describe the main purpose of today's work in 1-3 sentences.

Examples:
- stabilize public `/news` reliability without broad refactoring
- improve topic page composition safely
- align documentation with the actual repository state

---

## 3. Current Context Summary

Summarize the current project state relevant to today's work.

Include:
- what is already working
- what is currently unstable or incomplete
- any important carry-over context from the previous handoff

Keep this section short and concrete.

---

## 4. Inputs for Today's Planning

List the materials used to create today's strategy.

Typical inputs:
- `PROJECT_BRIEF.md`
- latest `DAILY_HANDOFF.md`
- QA notes from `QA_INBOX.md`
- structured QA from `QA_STRUCTURED.md`
- recent reports in `docs/reports/`
- specific user instructions

---

## 5. User-Observed Issues

List issues directly observed by the human during QA or real usage.

For each issue include:
- symptom
- where it appears
- why it matters

Example:
- homepage summary feels repetitive and weak in light mode
- topic page navigation is not obvious
- public detail route behavior is unclear on failure

---

## 6. Code / System Findings

List findings from analysis, reports, or repository inspection.

Include:
- real execution path findings
- architecture risks
- doc vs code mismatches
- operational concerns
- flaky or missing tests

This section should be evidence-based, not speculative.

---

## 7. Candidate Work Buckets

Group today's work into logical buckets.

Recommended bucket types:
- reliability
- bug fix
- feature
- docs alignment
- UX/UI polish
- test coverage
- harness improvement

Each bucket should include:
- bucket name
- why it exists
- rough scope

---

## 8. Priority Order

Order the candidate buckets.

Use:
1. highest priority
2. medium priority
3. optional / nice-to-have

Priority should reflect:
- production risk
- user impact
- unblock value
- change safety

---

## 9. Selected Work for Today

List only the buckets that should actually be worked on today.

For each selected bucket include:
- bucket name
- today's goal
- why selected now
- why not deferred

---

## 10. Step Breakdown

Break selected work into small, Codex-executable steps.

For each step include:

### Step N. Title

**Goal**
- exact goal of this step

**Target Area**
- controller / service / provider / repository / docs / tests / template / config

**Likely Files**
- list only likely involved files
- do not over-broaden

**Forbidden Scope**
- what must NOT be changed in this step

**Validation**
- how the result should be checked
- tests, manual behavior, report consistency, diff review, etc.

**Expected Output**
- implementation change / analysis report / doc update / test update

---

## 11. Recommended Agent Flow

Define the recommended subagent sequence for today's work.

Default:
1. `navi`
2. `reviewer`
3. `worker`
4. `reviewer`
5. `dockeeper`
6. `gitter`

If a different order is needed, explain why.

---

## 12. Codex Execution Notes

List execution guidance that Codex must follow today.

Examples:
- read `PROJECT_BRIEF.md`, `AGENTS.md`, `HARNESS_RULES.md`, `DEV_LOOP.md` first
- reuse the current date folder under `docs/ops/YYYY-MM-DD/`
- do not create daily docs in root or directly under `docs/ops/`
- read this strategy file before implementing steps
- keep changes minimal and traceable

---

## 13. Risks and Constraints

List today's important risks.

Examples:
- scope may expand if provider logic is coupled
- docs may be stale
- current test suite may not fully validate UI behavior
- feature work must not mix with refactor work

Be concise and explicit.

---

## 14. Deferrals

List things intentionally NOT included today.

For each deferral include:
- what is deferred
- why it is deferred
- when it should be reconsidered

---

## 15. Definition of Done for Today

Describe what counts as a successful day.

Examples:
- selected steps completed safely
- no unrelated file changes
- docs aligned if repository reality changed
- risks documented honestly
- next session can continue from updated handoff

---

## 16. Handoff Requirement

At the end of today's work, the next file must be generated:

- `docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md`

That handoff must reflect:
- completed steps
- partial steps
- new risks
- harness improvements
- next recommended steps