# DAILY_HANDOFF_FORMAT

## 1. Date

YYYY-MM-DD

---

## 2. Summary of Today

### What Was Done

Provide a concise but meaningful summary.

Focus on:
- actual progress
- meaningful changes
- impact

Avoid listing trivial edits.

---

## 3. Completed Work

List fully completed steps.

- Step X: description
- Step Y: description

Each must map to a defined step in TODAY_STRATEGY.

---

## 4. Partially Completed Work

Work that started but is incomplete.

For each:

- Step X:
  - current progress
  - what remains
  - why incomplete

---

## 5. Deferred Work

Work intentionally postponed.

For each:

- item
  - reason for deferral
  - when it should be reconsidered

---

## 6. Carry-over Candidates (CRITICAL)

🔥 This section is the most important for continuity.

List work that should be reconsidered in the next session.

For each item:

- item name
- origin (step / QA / bug / finding)
- previous status (partial / deferred / blocked)
- why it should continue
- risk if ignored
- suggested priority (high / medium / low)

⚠️ These items MUST be evaluated by the next planner.

---

## 7. Dropped / Rejected Work

List work that is no longer needed.

For each:

- item
- reason for dropping
- confirmation that it should not return

---

## 8. New Findings / Observations

Important discoveries during work.

Examples:
- hidden side effects
- unexpected behavior
- architectural inconsistencies
- doc vs code mismatch
- provider/API quirks

Must be concrete and actionable.

---

## 9. Risks Identified

List risks affecting future work.

- regression risks
- incomplete logic
- performance concerns
- unclear ownership

Each should be short and specific.

---

## 10. Documentation State

List documentation updates.

- updated docs
- outdated docs
- mismatches intentionally left unresolved

If no updates:
- explicitly state so

---

## 11. Harness Improvements (Very Important)

List improvements to the development process.

Examples:
- new rule added
- agent behavior refined
- repeated failure pattern identified
- workflow clarified

If none:
- explicitly say "no harness improvement today"

---

## 12. Known Mismatches (Code vs Docs)

List inconsistencies still remaining.

Example:
- README claims feature X, but partially implemented
- PROJECT_BRIEF outdated relative to current system

---

## 13. Next Recommended Steps

Actionable next steps.

Each must be:
- small
- safe
- well-scoped

---

## 14. Priority for Next Session

Order clearly:

1. highest priority
2. important
3. optional

Must reflect:
- carry-over importance
- user impact
- risk

---

## 15. Required Reading for Next Session

Must-read before continuing:

- PROJECT_BRIEF.md
- AGENTS.md
- HARNESS_RULES.md
- DEV_LOOP.md
- latest TODAY_STRATEGY.md
- THIS DAILY_HANDOFF.md

---

## 16. Open Questions / Clarifications Needed

List unresolved questions.

Examples:
- unclear requirement
- ambiguous behavior
- decision needed

---

## 17. Notes for Agents

Optional but useful:

- pitfalls
- constraints
- hidden assumptions

---

## 18. Definition of a Clean Handoff

A clean handoff means:

- next session can start without re-analysis
- carry-over items are clearly defined
- next step is obvious
- risks are visible
- no ambiguity exists