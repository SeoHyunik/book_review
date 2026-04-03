# DEV_LOOP

## 1. Purpose

This document defines the standard development operating loop for this repository.

The goal is not only to implement features, but to do so in a way that is:

- production-aware
- traceable
- minimal and safe
- reviewable
- accumulative over time

This loop is designed for human + GPT + Codex CLI + subagent collaboration.

Core principle:

> humans set direction, scope, and quality boundaries;
> agents analyze, implement, review, and help improve the system.

---

## 2. Core Operating Philosophy

This repository is developed through a harness-oriented loop.

That means:

- do not rely on a single prompt as the main control mechanism
- use repeatable analysis → planning → execution → review → handoff flow
- treat documentation as part of the operating system for agents
- preserve product intent, architecture boundaries, and production stability
- convert repeated mistakes into stronger rules, not longer prompts

---

## 3. Standard Development Loop

The default loop consists of the following stages:

0. Blueprint (On-Demand)
1. Morning Analysis
2. Human QA / Product Review
3. Strategy Planning
4. Step Breakdown
5. Codex Execution
6. Review and Re-plan
7. Daily Handoff

Each stage has a clear input, output, and owner.

---

## 4. Stage Definitions

### Stage 0. Blueprint (On-Demand)

#### Goal
Re-align product direction, constraints, and scope when needed.

#### When to Trigger
- product direction is unclear or drifting
- repeated implementation mismatches occur
- major feature pivot is being considered
- architecture decisions conflict with product goals
- project scope expansion requires redefinition

#### Output
- updated `PROJECT_BRIEF.md`

---

### Stage 1. Morning Analysis

#### Goal
Understand the current repository state before making decisions.

#### Output
- analysis report (docs/reports/*)

#### Agent
- `navi` → `reviewer`

---

### Stage 2. Human QA / Product Review

#### Goal
Capture real user-facing issues.

#### Output
- `docs/ops/YYYY-MM-DD/QA_INBOX.md`

#### Owner
- Human

---

### Stage 3. Strategy Planning

#### Goal
Convert QA + analysis into structured execution plan.

#### Input
- QA_INBOX
- DAILY_HANDOFF (previous day)
- reports

#### Output
- `docs/ops/YYYY-MM-DD/TODAY_STRATEGY.md`

#### Rule
- MUST read `docs/ops/TODAY_STRATEGY_FORMAT.md` before generating

#### Agent
- GPT / planner

---

### Stage 4. Step Breakdown

#### Goal
Break strategy into Codex-executable steps.

#### Output
- Step definitions inside TODAY_STRATEGY.md

---

### Stage 5. Codex Execution

#### Goal
Execute one step safely.

#### Flow
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

---

### Stage 6. Review and Re-plan

#### Goal
Evaluate result and decide next action.

#### Output
- accept / revise / next step
- harness improvement candidate

#### Agent
- reviewer + curator

---

### Stage 7. Daily Handoff

#### Goal
Preserve full context for next session.

#### Output
- `docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md`

#### Rule
- MUST read `docs/ops/DAILY_HANDOFF_FORMAT.md` before generating

---

## 5. Artifact Structure

### Static (root)

- PROJECT_BRIEF.md
- AGENTS.md
- HARNESS_RULES.md
- DEV_LOOP.md

---

### Architecture

- docs/architecture/*

---

### Reports

- docs/reports/*

---

### Ops (Critical)

#### Templates (fixed)

- docs/ops/TODAY_STRATEGY_FORMAT.md
- docs/ops/DAILY_HANDOFF_FORMAT.md

#### Daily (generated)

- docs/ops/YYYY-MM-DD/QA_INBOX.md
- docs/ops/YYYY-MM-DD/QA_STRUCTURED.md
- docs/ops/YYYY-MM-DD/TODAY_STRATEGY.md
- docs/ops/YYYY-MM-DD/DAILY_HANDOFF.md

#### Persistent

- docs/ops/HARNESS_FAILURES.md

---

## 6. Mandatory Ops Rules

- All daily documents MUST be inside `docs/ops/YYYY-MM-DD/`
- Never create daily docs in root or `docs/ops/`
- Always create date folder if missing
- Always read corresponding FORMAT before generation
- Never overwrite FORMAT files
- One day = one folder = one execution context

---

## 7. Escalation Rules

Escalate when:

- scope unclear
- architecture conflict
- multiple valid approaches
- change may affect production

---

## 8. Harness Evolution Rule

If mistake repeats:

- do NOT rewrite prompt only
- update:
    - HARNESS_RULES.md
    - AGENTS.md
    - subagents
    - docs structure

---

## 9. Definition of a Good Development Day

A good day means:

- analysis was accurate
- steps were small and safe
- no scope leak
- docs aligned or mismatch reported
- risks documented
- next step is obvious
- harness improved