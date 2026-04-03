# AGENTS

## 1. Mission

This repository is developed as a production-first Spring Boot monolith.

All AI-assisted work must preserve:

- correctness
- stability
- observability
- rollback safety
- explicit and traceable logic
- minimal safe change surface

This is not a playground repository.
Do not optimize for novelty.
Optimize for safe, explainable progress.

---

## 2. Required Reading Order

Before making any repository judgment, read in this order when relevant:

1. `PROJECT_BRIEF.md`
2. `DEV_LOOP.md`
3. `HARNESS_RULES.md`
4. `docs/architecture/java-goat-principles.md`
5. relevant reports in `docs/reports/`
6. step-specific files explicitly mentioned by the user

If code conflicts with documentation:
- trust the code
- report the mismatch clearly
- do not invent reconciliation

---

## 3. Core Working Principles

- Prefer minimal, targeted changes over broad rewrites
- Preserve existing Korean comments and user-facing Korean text
- Do not mix refactoring with feature work unless explicitly requested
- Follow explicit execution paths before proposing edits
- Respect layer boundaries: controller → service → provider → repository
- Prioritize production stability over elegance
- Avoid speculative improvements not required for the current step

---

## 4. Language Policy

- The user may instruct in Korean
- Internally analyze and execute in English
- Report findings, plans, and final summaries in Korean
- Keep code, identifiers, filenames, and commit messages in English unless explicitly requested otherwise
- Preserve existing Korean wording where already appropriate

---

## 5. Allowed Scope

By default, agents may:

- analyze repository structure
- trace execution paths
- review correctness and risk
- implement the smallest safe change within the explicitly approved scope
- update documentation only when implementation or repository reality changed
- prepare accurate commit messages after review is complete

Agents must NOT expand scope autonomously.

If additional work appears necessary:
- stop
- report why
- propose it as a next step
- do not silently include it

---

## 6. Forbidden Behavior

Unless explicitly requested, do NOT:

- modify unrelated files
- perform broad renames
- introduce new architecture
- change build configuration casually
- add new dependencies without approval
- alter database schema
- widen public API contracts silently
- change security behavior without explicit reason
- rewrite code for style alone
- overstate repository capabilities in docs
- commit before implementation and review are complete

---

## 7. Standard Agent Workflow

Use agents sequentially by default, not in parallel, unless tasks are clearly independent.

Preferred default order:

1. `navi`
   - trace the real execution flow
   - identify entry points, call chains, dependencies, configuration sensitivity, and blast radius
   - do not modify files

2. `reviewer`
   - review the current implementation using `navi` findings
   - identify correctness issues, regression risk, missing tests, and risky assumptions
   - do not modify files

3. `worker`
   - implement only the smallest safe change justified by prior analysis
   - avoid unrelated refactoring
   - keep change surface minimal

4. `reviewer`
   - re-review the implementation result
   - verify remaining risks, missing tests, and scope alignment

5. `dockeeper`
   - update documentation only if repository reality changed
   - keep docs aligned with actual implementation and product intent
   - do not invent guarantees or planned features as if already shipped

6. `gitter`
   - inspect git diff
   - determine proper conventional commit type
   - prepare a concise, accurate commit message
   - commit only after implementation and documentation are complete

---

## 8. Documentation Duties

Documentation is part of the operating system for agents.

After each meaningful step, check whether any of the following require updates:

- `README.md`
- `PROJECT_BRIEF.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `DAILY_HANDOFF.md`
- relevant report files in `docs/reports/`

Rules:
- do not update docs automatically unless repository state truly changed
- do not leave documentation knowingly stale without reporting it
- distinguish clearly between implemented, partial, and planned work

---

## 9. Reporting Contract

Every agent output should be structured and explicit.

When reporting a result, include when relevant:

- what was analyzed or changed
- why it mattered
- exact files involved
- what was NOT changed
- key risks
- missing tests or limitations
- recommended next step

Do not hide uncertainty.
Do not imply stronger verification than was actually performed.

---

## 10. Escalation Rules

Stop and escalate instead of proceeding when:

- product intent is ambiguous
- requested scope conflicts with architecture principles
- the minimal safe change cannot be isolated
- the task mixes multiple concerns
- code and docs conflict in a way that changes product judgment
- additional file changes seem required beyond approved scope
- production behavior may change materially

Escalation means:
- explain the issue clearly
- propose options if possible
- do not guess and proceed

---

## 11. Workflow Integration with DEV_LOOP

All work should fit into the standard loop defined in `DEV_LOOP.md`.

Default progression:

- Morning Analysis
- Human QA / Product Review
- Strategy Planning
- Step Breakdown
- Codex Execution
- Review and Re-plan
- Daily Handoff

Blueprint re-alignment is optional and only triggered when direction drift or product-level ambiguity appears.

---

## 12. Harness Evolution Rule

When the same mistake happens repeatedly, do not rely only on stronger prompting.

Instead, improve one or more of:

- `HARNESS_RULES.md`
- this `AGENTS.md`
- subagent instructions
- review checklist
- documentation structure
- test coverage
- daily handoff quality

Repeated mistakes should become stronger constraints.

---

## 13. Agent Substitution Rule

If a custom subagent cannot be spawned:

- report that explicitly
- explain which role is being substituted
- preserve the same responsibilities as closely as possible
- do not silently replace specialized analysis with shallow generic output

---

## 14. Final Priority Order

When guidance conflicts, follow this order:

1. `HARNESS_RULES.md`
2. `AGENTS.md`
3. `DEV_LOOP.md`
4. `PROJECT_BRIEF.md`
5. step prompt
6. general reasoning

The higher rule wins.

## 15. Documentation Structure and Generation Rules

### 15.1 Document Layers

Documents are divided into three layers:

#### Static Documents (always read first)
These define system behavior and must be considered before any action.

- PROJECT_BRIEF.md
- AGENTS.md
- HARNESS_RULES.md
- DEV_LOOP.md
- docs/architecture/*

#### Dynamic Templates (fixed operational formats)
These define how daily operational documents must be generated.

- docs/ops/TODAY_STRATEGY_FORMAT.md
- docs/ops/DAILY_HANDOFF_FORMAT.md

#### Dynamic Daily Documents (date-scoped operational state)
These represent the current working context for a specific date.

- docs/ops/YYYY-MM-DD/QA_INBOX.md
- docs/ops/YYYY-MM-DD/QA_STRUCTURED.md
- docs/ops/YYYY-MM-DD/TODAY_STRATEGY.md
- docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md
- docs/ops/HARNESS_FAILURES.md

#### Reports (analysis artifacts)
- docs/reports/*

---

### 15.2 Daily Ops Directory Rule

All daily operational outputs MUST follow a date-based directory structure.

Pattern:

docs/ops/YYYY-MM-DD/

Examples:

- docs/ops/2026-04-03/TODAY_STRATEGY.md
- docs/ops/2026-04-03/DAILY_HANDOFF.md

---

### 15.3 Format vs Generated Files

Template files must remain fixed:

- docs/ops/TODAY_STRATEGY_FORMAT.md
- docs/ops/DAILY_HANDOFF_FORMAT.md

Generated daily files must:

- be created inside the date directory
- never overwrite format files
- strictly follow the format structure
- use one date directory per working day

Daily generated files include:

- QA_INBOX.md
- QA_STRUCTURED.md
- TODAY_STRATEGY.md
- DAILY_HANDOFF.md

---

### 15.4 Mandatory Generation Procedure

Before generating any daily operational document:

1. determine today's date (YYYY-MM-DD)
2. check if docs/ops/YYYY-MM-DD/ exists
3. if not, create the directory
4. read the corresponding format file when applicable
5. generate the daily document inside the date directory
6. never create daily ops files in root or directly under docs/ops/

Format mapping:

- TODAY_STRATEGY.md → docs/ops/TODAY_STRATEGY_FORMAT.md
- DAILY_HANDOFF.md → docs/ops/DAILY_HANDOFF_FORMAT.md

Date-scoped daily documents:

- QA_INBOX.md
- QA_STRUCTURED.md
- TODAY_STRATEGY.md
- DAILY_HANDOFF.md

---

### 15.5 Reading Priority for Agents

When executing a step, agents should read:

Always:
- PROJECT_BRIEF.md
- AGENTS.md
- HARNESS_RULES.md
- DEV_LOOP.md

If available for today's date:
- docs/ops/YYYY-MM-DD/QA_INBOX.md
- docs/ops/YYYY-MM-DD/QA_STRUCTURED.md
- docs/ops/YYYY-MM-DD/TODAY_STRATEGY.md
- docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md

If generating daily documents:
- docs/ops/TODAY_STRATEGY_FORMAT.md
- docs/ops/DAILY_HANDOFF_FORMAT.md

Optionally:
- relevant files in docs/reports/

---

### 15.6 Failure Conditions

The following are considered violations:

- generating QA_INBOX.md, QA_STRUCTURED.md, TODAY_STRATEGY.md, or DAILY_HANDOFF.md in root or directly under docs/ops/
- overwriting *_FORMAT.md
- skipping date directory creation
- mixing multiple days into a single operational file
- ignoring existing date-scoped context files
- generating TODAY_STRATEGY.md without reading TODAY_STRATEGY_FORMAT.md
- generating DAILY_HANDOFF.md without reading DAILY_HANDOFF_FORMAT.md

If detected:
- report immediately
- do not proceed silently