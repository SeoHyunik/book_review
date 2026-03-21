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