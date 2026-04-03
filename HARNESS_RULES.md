# HARNESS_RULES

## 1. Purpose

This document defines hard constraints and enforceable rules for AI-assisted development.

Unlike AGENTS.md (guidelines), these rules represent boundaries that must not be violated.

If a rule conflicts with a prompt, the rule wins.

---

## 2. Core Principle

Do not rely on "better prompts" to prevent mistakes.

Instead:

- restrict behavior
- enforce boundaries
- validate outputs
- block unsafe changes

---

## 3. File Modification Constraints

### 3.1 Allowed Scope

- Only modify files explicitly mentioned in the step prompt
- If additional files seem necessary:
    - STOP
    - report the need
    - do not proceed autonomously

### 3.2 Forbidden Changes

The following are strictly forbidden unless explicitly allowed:

- modifying unrelated files
- changing build configuration (Gradle, dependencies)
- modifying security-related code
- altering database schema
- changing public API contracts
- renaming classes or packages broadly

---

## 4. Change Isolation Rules

- Do not mix multiple concerns in one step:
    - ❌ feature + refactor
    - ❌ bug fix + architecture change
- One step must produce one clear outcome

---

## 5. Architecture Protection Rules

Follow architecture principles strictly:

- Controller must not directly access repository
- Service layer owns business logic
- Provider layer must not leak into controller
- Do not bypass established flow

If violation seems necessary:

- STOP
- report reasoning
- do not implement

---

## 6. Documentation Consistency Rules

### 6.1 Mandatory Checks

After each step:

- check if code and documentation diverged
- if mismatch exists:
    - report explicitly
    - propose minimal update

### 6.2 Source of Truth

If code and docs conflict:

> trust the code, report the documentation issue

---

## 7. Language and Encoding Rules

- Preserve all Korean comments and strings
- Do not change Korean text unless explicitly requested
- Do not introduce encoding issues (UTF-8 BOM, etc.)
- Do not translate existing user-facing content

---

## 8. Dependency and Configuration Rules

- Do not introduce new libraries without explicit approval
- Do not upgrade major versions automatically
- Do not modify Gradle plugins unless required and justified
- Avoid changes that may break build reproducibility

---

## 9. Testing and Verification Rules

### 9.1 Required Behavior

Every implementation step must include:

- explanation of change
- affected files
- expected behavior change
- verification method

### 9.2 If Tests Exist

- ensure existing tests are not broken
- do not delete tests to make build pass
- update tests only when behavior is intentionally changed

---

## 10. Fail-safe Rules

When uncertain:

- do NOT guess
- do NOT proceed optimistically

Instead:

- state uncertainty
- request clarification
- or provide analysis only

---

## 11. Scope Escalation Rules

Escalate instead of proceeding when:

- required change exceeds defined scope
- architectural conflict appears
- multiple alternative implementations exist
- change may affect production behavior significantly

---

## 12. Commit Safety Rules

- Do not generate commits blindly
- Commit only after:
    - scope is validated
    - changes are reviewed
    - behavior is understood

---

## 13. Report Requirements

Every step must produce a structured output:

- what was changed
- why it was changed
- what was NOT changed (important)
- potential risks
- next possible step

---

## 14. Ops Document Generation Rules (🔥 NEW)

### 14.1 Directory Rule

All daily operational documents MUST be created under:

docs/ops/YYYY-MM-DD/

Never create operational documents in:
- repository root
- docs/ops/ (directly)

---

### 14.2 Format Enforcement

The following files are templates:

- docs/ops/TODAY_STRATEGY_FORMAT.md
- docs/ops/DAILY_HANDOFF_FORMAT.md

Rules:

- MUST read format file before generating output
- MUST NOT overwrite format files
- MUST follow structure defined in format

---

### 14.3 Daily Documents

The following are considered daily operational documents:

- QA_INBOX.md
- QA_STRUCTURED.md
- TODAY_STRATEGY.md
- DAILY_HANDOFF.md

Rules:

- MUST exist inside date folder
- MUST NOT exist outside date folder
- MUST represent a single day only

---

### 14.4 Generation Procedure

Before generating any daily document:

1. determine current date (YYYY-MM-DD)
2. check if docs/ops/YYYY-MM-DD/ exists
3. if not, create it
4. read corresponding FORMAT file (if applicable)
5. generate file inside date folder

---

### 14.5 Reuse Rule

- If today's folder already exists → reuse it
- Do NOT create multiple folders for the same date

---

### 14.6 Failure Conditions

The following are violations:

- generating TODAY_STRATEGY.md outside date folder
- generating DAILY_HANDOFF.md outside date folder
- generating QA_INBOX.md in root
- overwriting *_FORMAT.md
- skipping date folder creation
- mixing multiple days into one file

If violation detected:

- STOP
- report immediately
- do not proceed

---

## 15. Harness Evolution Rule

When a mistake repeats:

DO NOT:
- just rewrite prompt

DO:
- add rule here
- improve AGENTS.md
- improve subagent instructions
- suggest test or validation

---

## 16. Forbidden Anti-patterns

- "just in case" refactoring
- silent behavior change
- broad rename without request
- speculative architecture changes
- mixing unrelated fixes
- modifying code without understanding call path

---

## 17. Priority Order

When rules conflict:

1. HARNESS_RULES.md
2. AGENTS.md
3. DEV_LOOP.md
4. Step prompt
5. General reasoning

---

## 18. Expected Outcome

Following these rules should result in:

- smaller, safer changes
- predictable behavior
- reduced regression risk
- clearer reasoning
- easier debugging
- cumulative system improvement over time