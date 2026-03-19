# 🐐 JAVA GOAT Principles
> Production-grade Java/Spring engineering standards for scalable, maintainable, high-performance systems

---
# 1. Core Philosophy

## 1.1 Production First
This system is a **live production system**, not a sample project.

All changes must prioritize:
- correctness
- stability
- observability
- rollback safety

---
## 1.2 Minimal Change Principle
- Prefer **small, incremental, safe changes**
- Avoid large refactors unless explicitly required
- Always define the **smallest effective change surface**

---
## 1.3 Explicit Over Implicit
- Avoid hidden logic or side effects
- Prefer readability over clever abstraction
- Code must clearly communicate intent

---
# 2. Layered Architecture Rules

## 2.1 Layer Responsibilities

| Layer        | Responsibility |
|-------------|----------------|
| Controller  | Request/response mapping only |
| Service     | Application orchestration |
| Provider    | External API adaptation |
| Repository  | Persistence only |
| Domain      | Core business model |

---
## 2.2 Strict Boundaries

### Controller
- Must NOT contain business logic
- Only maps input/output

### Service
- Coordinates use cases
- Does NOT perform low-level operations

### Provider
- Only responsible for external API interaction
- Must NOT contain orchestration logic

### Repository
- Only data access
- No business rules

### Domain
- Pure business representation
- Must NOT depend on infrastructure

---
# 3. SOLID Principles (Mandatory)

## 3.1 SRP — Single Responsibility Principle
A class must have only one reason to change.

✔ Good:
- Provider handles API only
- Service handles orchestration only

❌ Bad:
- Class handling API + persistence + ranking

---
## 3.2 OCP — Open/Closed Principle
The system must be open for extension, closed for modification.

✔ New provider should NOT require modifying core logic

❌ Adding provider requires editing multiple switch/if statements

---
## 3.3 LSP — Liskov Substitution Principle
Implementations must behave consistently.

✔ All providers must:
- return consistent DTOs
- follow same failure semantics

---
## 3.4 ISP — Interface Segregation Principle
Interfaces must be minimal and focused.

✔ Good:
```java
interface NewsSourceProvider {
    List<ExternalNewsItem> fetchTopHeadlines();
}
````

❌ Bad:

- Interface with unrelated methods

---
## 3.5 DIP — Dependency Inversion Principle

✔ Depend on abstractions  
✔ Use constructor injection  
✔ Avoid concrete dependencies

---
# 4. Provider Architecture (Critical)

## 4.1 Provider = Strategy Pattern

Each provider must:

- implement a common interface

- be fully replaceable

- have zero knowledge of other providers

---
## 4.2 Provider Responsibilities

✔ Allowed:

- API request construction

- response parsing

- normalization

- minimal filtering

❌ Forbidden:

- orchestration logic

- ranking logic

- persistence logic

- cross-provider logic

---
## 4.3 Selector Responsibilities

Selector must:

- decide provider execution order

- orchestrate provider calls

- merge results

Selector must NOT:

- contain parsing logic

- duplicate provider behavior

---
## 4.4 Ingestion Responsibilities

Ingestion Service must:

- orchestrate flow

- persist results

- trigger async tasks

Must NOT:

- duplicate provider logic

- reimplement filtering rules unnecessarily

---
# 5. Design Pattern Guidelines

## 5.1 Strategy Pattern (Required)

Used for:

- providers

- ranking

- policies

Must be:

- interface-driven

- easily replaceable

---
## 5.2 Factory Pattern (Use Carefully)

Use ONLY when:

- runtime resolution is required

- provider lookup by key is needed


Do NOT use if:

- Spring DI already solves the problem

---
## 5.3 Avoid Pattern Abuse

❌ Do NOT:

- add abstraction without real need

- introduce patterns for aesthetics

✔ Patterns must solve real problems

---
# 6. Code Quality Standards

## 6.1 Naming

- Class: PascalCase

- Method/Variable: camelCase

- Constant: UPPER_SNAKE_CASE

Names must clearly express intent.

---
## 6.2 Method Design

- Prefer under 30 lines

- Single responsibility

- Avoid deep nesting (>2 levels)

---
## 6.3 Null Handling

✔ Prefer Optional  
❌ Avoid returning null

---
## 6.4 Immutability

✔ Use final where possible  
✔ Prefer immutable objects

---
## 6.5 Logging

✔ Log:

- failures

- external API results (summary level)

- decision points

❌ Do NOT log:

- secrets

- excessive noise

---
# 7. Performance Principles

## 7.1 External API

- Minimize calls

- Avoid duplicates

- Batch when possible

---
## 7.2 Memory

- Avoid unnecessary object creation

- Use streaming for large datasets

---
## 7.3 Database

- Avoid N+1 queries

- Use indexes

- Keep queries simple

---
## 7.4 Async Processing

✔ Use when beneficial  
❌ Avoid uncontrolled async execution

---
# 8. Error Handling

✔ Fail fast  
✔ Provide meaningful errors

❌ Do NOT:

- swallow exceptions

- ignore failures silently

---
# 9. Configuration Rules

✔ Use config for:

- API keys

- thresholds

- feature flags

❌ Do NOT hardcode:

- URLs

- limits

- environment values

---
# 10. Codex Execution Rules

## Before Coding

1. Understand full flow

2. Identify responsibilities

3. Check SOLID violations

4. Define minimal change scope

---
## During Coding

- Do NOT refactor unrelated code

- Do NOT introduce large abstractions

- Follow existing architecture

---
## After Coding

- Validate correctness

- Validate boundaries

- Ensure no side effects

---
# 11. Refactoring Rules

✔ Allowed:

- responsibility separation

- duplication removal

- naming improvements

❌ Forbidden:

- large redesign without instruction

- mixing refactor + feature changes

---
# 12. Golden Rules

1. Clarity over cleverness

2. Stability over abstraction

3. Minimal change over perfect design

4. Measured performance over premature optimization

5. Solve real problems, not theoretical ones

---
# Final Note

This is a production system.

All changes must:

- improve maintainability

- preserve stability

- respect architecture

When in doubt:  
→ choose the simplest, safest solution
