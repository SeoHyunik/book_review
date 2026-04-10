# TODAY_STRATEGY

## 1. Date
2026-04-10

---

## 2. Strategy Objective
- Restore daytime Naver news collection with the smallest safe filter adjustment so public freshness recovers without broadening the news set beyond the intended market topics.
- Capture the external index-related API keys and manual source dependencies in a concise inventory so future market-data work is not blocked by unknown prerequisites.

---

## 3. Current Context Summary
- The current product pressure is freshness, not feature expansion: daytime news collection is empty, and that weakens downstream interpretation quality.
- The requested ingestion fix is intentionally narrow: widen keyword filters first, then narrow the collected set back to KOSPI/KOSDAQ-related articles.
- The broader market-data path is still prerequisite-heavy because the team does not yet have a clean inventory of required index-related API keys and external inputs.
- Yesterday's harness continuity issue remains real, but today's QA points to product-facing work first; harness cleanup stays visible but off the critical path for today.

---

## 4. Carry-over from Previous Session
- Daily handoff completion gate
  - previous status: blocked
  - why it was not completed: the previous session documented the gap, but the session ended before a reusable handoff was written
  - still relevant: yes
  - decision today: defer again
- Workday-state consistency check
  - previous status: blocked
  - why it was not completed: the harness state can drift from the date-scoped ops artifacts, and no closure fix was implemented
  - still relevant: yes
  - decision today: defer again
- Daily ops corruption detection
  - previous status: partial
  - why it was not completed: formatting and readability issues were observed, but no harness enforcement change was made
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-10/QA_STRUCTURED.md`
- `docs/ops/2026-04-10/QA_INBOX.md`
- `docs/ops/2026-04-09/DAILY_HANDOFF.md`
- `docs/reports/2026-04-08-step-2-daily-ops-continuity-check.md`
- `docs/reports/2026-04-07-step-2-re-review-and-handoff-check.md`
- explicit user instruction to use QA_STRUCTURED as the primary planning input and QA_INBOX only as a cross-check

---

## 6. User-Observed Issues
- Symptom: Naver news is not being collected during the daytime.
  - Where it appears: news collection pipeline / Naver crawler
  - Why it matters: public freshness drops and downstream interpretation quality degrades when the feed is empty
- Symptom: the team does not yet have a clean list of index-related API keys and other manual inputs.
  - Where it appears: external market-data integration planning
  - Why it matters: implementation can stall if required external dependencies are not identified early

---

## 7. Code / System Findings
- The structured QA and the raw inbox are aligned on the main issue: daytime Naver collection is missing and needs a careful filter adjustment, not a broad product rewrite.
- The raw inbox adds implementation nuance, namely that the filter should be widened first and then narrowed to KOSPI/KOSDAQ-related articles; that is an inference from the QA text, not a code trace.
- The 2026-04-08 continuity report shows the daily QA and strategy chain was internally consistent, and the actual operational gap was the empty handoff file.
- No code trace was performed in this planning step, so the next execution pass still needs to confirm the exact crawler and filter path before changing behavior.

---

## 8. Candidate Work Buckets
- Reliability: daytime Naver collection recovery
  - why it exists: public news freshness is degraded today
  - scope: trace the filter path, widen the keyword set only as far as needed, and verify the feed resumes with KOSPI/KOSDAQ relevance intact
- Docs alignment: index-related API key and source inventory
  - why it exists: future market-data work needs a clear prerequisite list
  - scope: document the required external keys, inputs, and manual setup items in a concise report
- Harness improvement: daily handoff and closure consistency
  - why it exists: the previous session ended with a reusable handoff gap
  - scope: keep the issue visible, but do not expand into it today

---

## 9. Priority Order
1. Reliability: daytime Naver collection recovery
2. Docs alignment: index-related API key and source inventory
3. Harness improvement: daily handoff and closure consistency

---

## 10. Selection Logic
- The carry-over harness items remain relevant, but they are not selected today because they do not unblock the immediate public-facing freshness issue.
- The new QA shifts priority toward the ingestion problem because it has the most direct user impact and the smallest safe execution path.
- The dependency inventory is selected second because it reduces the chance that later market-data work stalls on unknown external prerequisites.
- The trade-off is deliberate: keep the harness follow-up explicit, but avoid mixing it into the product fix so the step stays small and safe.

---

## 11. Selected Work for Today
- Reliability / ingestion
  - goal: restore daytime Naver collection with a minimal filter adjustment
  - why selected: it directly addresses the degraded public feed
  - why not deferred: the freshness problem is current and user-visible
- Docs alignment / dependency inventory
  - goal: produce a concise inventory of index-related API keys and manual source inputs
  - why selected: it prevents blocked or ambiguous follow-up work
  - why not deferred: this is a bounded prerequisite task and can be completed without changing product behavior

---

## 12. Step Breakdown
### Step 1. Trace and Restore Daytime Naver Collection

**Goal**
- Identify the exact keyword-filter and scheduling path that suppresses daytime collection, then make the smallest safe adjustment so the feed resumes.

**Target Area**
- service
- provider
- config
- tests

**Likely Files**
- Naver crawler or news ingestion service classes
- keyword filter configuration
- ingestion scheduler or job trigger code
- related integration or regression tests

**Forbidden Scope**
- no broad refactor
- no database schema change
- no unrelated crawler changes
- no public API contract changes

**Validation**
- confirm the daytime feed path is reached again
- verify the collected set stays on-topic after widening then narrowing the filter
- run or update the most relevant ingestion tests

**Expected Output**
- code
- tests

### Step 2. Inventory Required Index API Keys and Manual Inputs

**Goal**
- Document the external index-related API keys and source dependencies the team must obtain manually before market-data work can proceed.

**Target Area**
- docs
- report

**Likely Files**
- `docs/reports/2026-04-10-index-api-key-inventory.md`
- any existing market-data notes that need a minimal cross-reference update

**Forbidden Scope**
- no code changes
- no new architecture
- no speculative market-data implementation

**Validation**
- check the inventory is complete enough for a developer to start the next market-data step without guessing prerequisites
- cross-check the list against the current product brief and QA notes

**Expected Output**
- report
- optional documentation note if the repository already has a natural place for it

---

## 13. Recommended Agent Flow
- Default flow applies:
  1. navi
  2. reviewer
  3. worker
  4. reviewer
  5. dockeeper
  6. gitter
- Keep Step 1 and Step 2 separate so the ingestion fix can be validated before any documentation follow-up is finalized.

---

## 14. Codex Execution Notes
- Codex must read:
  - `PROJECT_BRIEF.md`
  - `AGENTS.md`
  - `HARNESS_RULES.md`
  - `DEV_LOOP.md`
  - this strategy file
- Codex must use only `docs/ops/2026-04-10/` for daily ops artifacts.
- Codex must not create or modify files outside the selected step scope.
- Codex must execute the smallest safe change, then validate before any commit.

---

## 15. Risks and Constraints
- The Naver filter change could broaden the feed too far if the keyword narrowing is not preserved.
- The index API inventory may require external source lookup, so the step can stall if the prerequisite list is not discoverable from the repo and reports alone.
- The raw inbox is noisy at the text level, so the structured QA remains the source of truth for scope selection.
- Harness cleanup remains necessary, but mixing it into today's product work would increase scope without helping the freshness issue.

---

## 16. Deferrals
- Harness completion gate
  - reason: not needed to unblock today's user-visible freshness problem
  - revisit: after the ingestion fix is stable
- Workday-state consistency validation
  - reason: important, but separate from the product-facing QA items selected today
  - revisit: during the next harness-focused pass
- Daily ops corruption detection
  - reason: still relevant, but no new enforcement change is required to start today's work
  - revisit: when the harness work is reopened

---

## 17. Definition of Done for Today
- Daytime Naver collection resumes with the intended topic constraints preserved.
- The index-related API prerequisite list is written down clearly enough to support follow-on work.
- No unrelated files are changed.
- The carry-over harness items remain visible but are not accidentally expanded into today's scope.
- The next session can continue from a clear handoff.

---

## 18. Handoff Requirement

`docs/ops/2026-04-10/DAILY_HANDOFF.md`

