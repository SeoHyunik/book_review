# DAILY_HANDOFF

## 1. Date

YYYY-MM-DD

---

## 2. Summary of Today

### What Was Done

- high-level summary of completed work
- focus on meaningful changes, not minor edits

Example:
- fixed null handling in provider layer for NewsAPI fallback
- added minimal guard logic in service layer
- updated README to reflect actual supported providers

---

## 3. Completed Steps

List of steps that were fully completed.

- Step X: description
- Step Y: description

Each step should map to a clear intent defined in TODAY_STRATEGY.md.

---

## 4. Partially Completed / Deferred Work

Work that was started but not fully finished.

- Step X:
    - current status
    - what is missing
    - why it was not completed

---

## 5. New Findings / Observations

Important discoveries during development.

- hidden side effects
- unexpected behavior
- architectural inconsistencies
- doc vs code mismatch
- provider/API quirks

These should be concrete and actionable.

---

## 6. Risks Identified

List risks that may affect future work.

- regression risk
- incomplete edge case handling
- potential production impact
- unclear ownership of logic
- performance concerns

Each risk should be short and specific.

---

## 7. Documentation Changes

List documents that were updated today.

- README.md
- PROJECT_BRIEF.md
- AGENTS.md
- HARNESS_RULES.md
- reports/*

If documents were NOT updated despite mismatch, explicitly state that.

---

## 8. Harness Improvements (Very Important)

List any harness-related improvements made today.

- new rule added to HARNESS_RULES.md
- clarification added to AGENTS.md
- workflow refinement
- subagent instruction improvement
- repeated mistake identified

If none:
- explicitly state "no harness improvement today"

---

## 9. Known Mismatches (Code vs Docs)

List known inconsistencies that still remain.

Example:
- README states GNews fallback, but not implemented
- PROJECT_BRIEF implies full provider abstraction, but partially implemented

This helps avoid future confusion.

---

## 10. Next Recommended Steps

Clear, actionable suggestions for the next session.

- Step A: description
- Step B: description

Must be small, safe, and aligned with current state.

---

## 11. Priority for Tomorrow

Order of execution:

1. most critical
2. important but not blocking
3. optional / improvement

---

## 12. Required Reading for Next Session

List documents that MUST be read before continuing.

- PROJECT_BRIEF.md
- AGENTS.md
- specific report files
- relevant source files

---

## 13. Open Questions / Clarifications Needed

List unresolved questions.

- unclear product behavior
- ambiguous requirement
- missing spec
- decision needed from human

---

## 14. Notes for Agents

Optional but useful:

- constraints to remember
- pitfalls to avoid
- context not obvious from code

---

## 15. Definition of a Clean Handoff

A good handoff means:

- next session can start without re-analysis
- no ambiguity about current progress
- next step is obvious and safe
- risks are visible
- documents are aligned or mismatches are declared