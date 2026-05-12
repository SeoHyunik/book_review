# Project CLAUDE Bridge

## Purpose

This file is a small project-local bridge document for repositories connected to the portable SSD harness kit.

Its job is to:

- point the project session toward the harness core
- reinforce minimal repository-bridge behavior
- keep daily operational artifacts in the external workspace
- reference the active profile and core operating documents
- stay small, stable, and reusable

This file is NOT the harness core.
It is only a repository bridge.

---

## Core Boundary

Treat this repository as connected to an external harness.

That means:

- the harness core lives on the SSD at `D:\harness-kit`
- the repository is only a minimal local bridge
- the external workspace is the operational state location
- daily operational artifacts must not be created inside this repository

Do not create these inside the repository:

- `REQUEST_INBOX.md`
- `REQUEST_STRUCTURED.md`
- `TODAY_STRATEGY.md`
- `DAILY_HANDOFF.md`
- `HARNESS_FAILURES.md`
- `WORKDAY_STATE.json`

Those belong in the external workspace.

---

## Active Bridge Values

Active profile for this repository:

- `corp-default`

External workspace root for this repository:

- `D:\harness-workspaces\auziraum`

Project name:

- `auziraum`

Harness kit root:

- `D:\harness-kit`

These placeholders must be replaced when the bridge file is generated.

---

## Required Core Reading

Before making meaningful judgments, read these from the harness kit when available:

- `D:\harness-kit/core/AGENTS.core.md`
- `D:\harness-kit/core/HARNESS_RULES.core.md`
- `D:\harness-kit/core/DEV_LOOP.core.md`
- `D:\harness-kit/core/TODAY_STRATEGY_FORMAT.md`
- `D:\harness-kit/core/DAILY_HANDOFF_FORMAT.md`

Also read the active profile selected for this repository:

- `D:\harness-kit/profiles/corp-default.yaml`

If the active profile points to stack-specific architecture policy documents, read those too.

Example:

- `D:\harness-kit/profiles/architecture/java-goat-principles.md`

---

## Repository Bridge Rules

Inside the repository, keep the harness bridge minimal.

Typical allowed bridge files:

- `.claude/settings.local.json`
- `.claude/CLAUDE.md`
- `tools/run-cycle.ps1`

Do not expand the repository bridge casually.

Do not create operational state, planning, handoff, or failure artifacts inside the repository.

If a file is needed only for day-to-day execution state, it belongs in the external workspace, not here.

---

## External Workspace Rule

Use the external workspace as the operational state location:

- root: `D:\harness-workspaces\auziraum`
- one day = one date folder
- daily artifacts live under `D:\harness-workspaces\auziraum/<YYYY-MM-DD>/`

Typical daily files under the date folder:

- `REQUEST_INBOX.md`
- `REQUEST_STRUCTURED.md`
- `TODAY_STRATEGY.md`
- `DAILY_HANDOFF.md`
- `WORKDAY_STATE.json`

Persistent cross-day workspace files may live at the workspace root when the harness defines them there.

---

## Execution Entry Rule

Prefer using the prepared bridge entrypoint inside the repository when available:

- `tools/run-cycle.ps1`

That bridge should delegate to the SSD-side harness scripts rather than re-implementing orchestration logic locally.

If repository bridge files and SSD-side core scripts disagree, treat the SSD-side implementation as the current execution truth and report the mismatch.

---

## Code vs Documentation Rule

If code conflicts with docs:

- trust the code as the current implementation truth
- report the mismatch clearly
- do not invent reconciliation

If project docs are stale, do not over-trust them just because they are easy to read.

---

## Working Style Rule

Prefer:

- small safe changes
- explicit logic
- honest validation
- narrow scope
- resumable work
- accurate handoff

Avoid:

- broad refactors
- speculative architecture changes
- repository pollution with operational artifacts
- pretending incomplete work is complete

---

## Notes

This bridge file should remain short.

If a rule belongs to every project, it should live in the harness core.

If a rule belongs only to a specific stack or environment, it should live in the active profile or profile-linked architecture policy.
