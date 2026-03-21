# Project Agent Rules

## Core development principles
- This is a production-first Spring Boot monolith.
- Prefer minimal, targeted changes over broad rewrites.
- Preserve existing Korean comments and user-facing Korean strings.
- Do not mix refactoring with feature work unless explicitly requested.
- Follow explicit, traceable execution paths before proposing changes.
- Respect layer boundaries: controller, service, provider, repository.
- Prioritize rollback safety, operational stability, and testability.

## Language policy
- The user may give instructions in Korean.
- Internally, analyze and execute development tasks in English.
- Report findings, plans, and final summaries in Korean.
- Keep code, identifiers, filenames, and commit messages in English unless explicitly requested otherwise.
- If existing Korean text already exists in code or templates, preserve it unless a change is required.

## Working style
- First understand the current code path.
- Then identify the smallest safe change.
- Then verify impact and missing tests.
- Do not modify files unless explicitly asked.

## Required context before changes
- Read PROJECT_BRIEF.md before making product-facing changes.
- Read docs-architecture/java-goat-principles.md before making architecture-sensitive changes.
- If current code conflicts with product intent, report the mismatch before editing.

## Sequential agent workflow

Use agents sequentially by default, not in parallel, unless the tasks are clearly independent.

Preferred order:
1. `navi`
    - understand the real execution flow
    - identify entry points, call chains, dependencies, and blast radius
    - do not modify files

2. `reviewer`
    - review the current implementation based on the `navi` findings
    - identify correctness issues, regression risks, and missing tests
    - do not modify files

3. `worker`
    - implement the smallest safe change only when analysis shows a real need
    - avoid unrelated refactoring
    - keep the change surface minimal

4. `reviewer`
    - re-review the changes after implementation
    - verify remaining risks and missing tests

5. `dockeeper`
    - update `README.md` or related documentation only if the code/documentation state has changed
    - keep documentation aligned with actual implementation
    - do not overstate incomplete features

6. `gitter`
    - inspect `git diff`
    - choose the proper conventional commit type
    - create a concise and accurate commit message
    - commit only after the implementation and documentation steps are complete

Workflow rules:
- Read `PROJECT_BRIEF.md` before any product-facing judgment.
- Read `docs-architecture/java-goat-principles.md` before architecture-sensitive judgment.
- Use parallel agents only for clearly independent analysis tasks.
- If a custom subagent cannot be spawned, report that explicitly before substituting with a generic agent.
- Prefer sequential delegation for reliability and traceability.