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
- any important carry-over context from the previous session

Keep this section short and concrete.

---

## 4. Carry-over from Previous Session

List unfinished or deferred work from the most recent DAILY_HANDOFF.

For each item include:
- item name
- previous status (partial / deferred / blocked)
- why it was not completed
- whether it is still relevant
- decision today:
    - continue now
    - defer again
    - drop

⚠️ IMPORTANT:
Do NOT ignore carry-over work.
All carry-over items must be explicitly evaluated.

---

## 5. Inputs for Today's Planning

List the materials used to create today's strategy.

Typical inputs:
- `PROJECT_BRIEF.md`
- latest `DAILY_HANDOFF.md`
- QA notes from `QA_INBOX.md`
- structured QA from `QA_STRUCTURED.md`
- recent reports in `docs/reports/`
- specific user instructions

---

## 6. User-Observed Issues

List issues directly observed by the human during QA or real usage.

For each issue include:
- symptom
- where it appears
- why it matters

---

## 7. Code / System Findings

List findings from analysis, reports, or repository inspection.

Include:
- execution path findings
- architecture risks
- doc vs code mismatches
- operational concerns
- flaky or missing tests

Must be evidence-based.

---

## 8. Candidate Work Buckets

Group work into logical buckets.

Recommended types:
- reliability
- bug fix
- feature
- docs alignment
- UX/UI polish
- test coverage
- harness improvement

Each bucket includes:
- bucket name
- why it exists
- scope

---

## 9. Priority Order

Order all candidate buckets.

Use:
1. highest priority
2. medium priority
3. optional

Priority must consider:
- production risk
- user impact
- unblock value
- safety of change

---

## 10. Selection Logic

Explain HOW today's work was selected.

Must explicitly answer:
- why carry-over items are or are not selected
- how new QA influenced priorities
- what trade-offs were made

This section prevents arbitrary planning.

---

## 11. Selected Work for Today

List ONLY the work to be executed today.

For each:
- bucket name
- goal
- why selected
- why not deferred

---

## 12. Step Breakdown

Break selected work into small executable steps.

### Step N. Title

**Goal**
- exact objective

**Target Area**
- controller / service / provider / repository / docs / tests / template / config

**Likely Files**
- realistic subset only

**Forbidden Scope**
- explicitly what must NOT change

**Validation**
- how correctness is verified

**Expected Output**
- code / doc / test / report

---

## 13. Recommended Agent Flow

Default:
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

Explain if different.

---

## 14. Codex Execution Notes

Codex must:

- read:
    - `PROJECT_BRIEF.md`
    - `AGENTS.md`
    - `HARNESS_RULES.md`
    - `DEV_LOOP.md`
    - THIS strategy file

- use:
    - `docs/ops/YYYY-MM-DD/` folder only

- must NOT:
    - create docs outside ops/date folder
    - modify unrelated files
    - mix multiple steps

- must:
    - execute step-by-step
    - validate before commit

---

## 15. Risks and Constraints

List today's key risks.

Examples:
- scope expansion risk
- doc inconsistency
- incomplete tests
- coupling risks

---

## 16. Deferrals

List intentionally postponed work.

For each:
- item
- reason
- when to revisit

---

## 17. Definition of Done for Today

Success means:

- selected steps completed safely
- no unrelated changes
- carry-over handled correctly
- risks documented
- next session can continue seamlessly

---

## 18. Handoff Requirement

At end of work, MUST generate:

`docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps