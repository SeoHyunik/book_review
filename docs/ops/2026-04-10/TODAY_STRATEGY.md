# TODAY_STRATEGY

## 1. Date
2026-04-10

---

## 2. Strategy Objective
- Restore daytime Naver news collection with the smallest safe filter adjustment so public freshness recovers without broadening the feed beyond the intended market topics.

---

## 3. Current Context Summary
- The immediate product issue is freshness: daytime collection is empty, which weakens downstream interpretation quality.
- The requested fix is intentionally narrow: widen the collection filter first, then narrow the collected set back to KOSPI- and KOSDAQ-related articles.
- The broader index-related API key inventory is still useful, but it is a prerequisite and not the highest-priority execution item for today.
- The previous session also surfaced a harness continuity problem, but that remains off the critical path for this product-focused day.

---

## 4. Carry-over from Previous Session
- Daily handoff completion gate
  - previous status: blocked
  - why it was not completed: the previous session documented the gap, but the day ended before a reusable handoff was written
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
- explicit user instruction to use `QA_STRUCTURED.md` as the primary planning input and `QA_INBOX.md` only as a cross-check

---

## 6. User-Observed Issues
- Symptom: daytime Naver news is not being collected.
  - Where it appears: news collection pipeline / Naver crawler
  - Why it matters: public freshness drops and downstream interpretation quality degrades when the feed is empty
- Symptom: the team does not yet have a clean list of index-related API keys and other manual inputs.
  - Where it appears: external market-data integration planning
  - Why it matters: implementation can stall if required external dependencies are not identified early

---

## 7. Code / System Findings
- `QA_STRUCTURED.md` and `QA_INBOX.md` agree on the core issue: daytime Naver collection is missing and needs a narrow filter adjustment, not a broad rewrite.
- The raw inbox adds an implementation hint that the filter should be widened first and then narrowed to KOSPI/KOSDAQ-related articles; that is an inference from the raw text, not a code trace.
- The raw inbox also mentions the API-key inventory, but the structured QA separates it as a broader product decision rather than an immediate implementation item.
- The 2026-04-09 handoff confirms the operational continuity gap remains real, but it is not the selected product work for today.
- No code trace was performed in this planning step, so the exact crawler and filter path still needs to be confirmed during execution.

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
- The structured QA marks the ingestion issue as the only implementation-ready item selected today, so it becomes the primary work item.
- The dependency inventory is useful but deferred because it is a broader product decision and not required to restore today’s freshness problem.
- The harness carry-over items remain relevant, but they are intentionally deferred so the step stays small and safe.
- The raw inbox did not materially change the selection: it reinforced the same freshness issue and added only noisy phrasing around the prerequisite inventory.

---

## 11. Selected Work for Today
- Reliability / ingestion
  - goal: restore daytime Naver collection with a minimal filter adjustment
  - why selected: it directly addresses the degraded public feed
  - why not deferred: the freshness problem is current and user-visible

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

---

## 13. Recommended Agent Flow
- Default flow applies for the selected ingestion step:
  1. navi
  2. reviewer
  3. worker
  4. reviewer
  5. dockeeper
  6. gitter

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
- The raw inbox is noisy at the text level, so the structured QA remains the source of truth for scope selection.
- The index API inventory remains a useful follow-up, but it should not pull focus away from the freshness fix.
- Harness cleanup remains necessary, but mixing it into today's product work would increase scope without helping the freshness issue.

---

## 16. Deferrals
- Index-related API key and source inventory
  - reason: important prerequisite work, but not required to restore today's daytime feed
  - when to revisit: after the ingestion fix is stable
- Daily handoff completion gate
  - reason: not needed to unblock today's user-visible freshness problem
  - when to revisit: after the ingestion fix is stable
- Workday-state consistency validation
  - reason: important, but separate from the product-facing QA item selected today
  - when to revisit: during the next harness-focused pass
- Daily ops corruption detection
  - reason: still relevant, but no new enforcement change is required to start today's work
  - when to revisit: when the harness work is reopened

---

## 17. Definition of Done for Today
- Daytime Naver collection resumes with the intended topic constraints preserved.
- No unrelated files are changed.
- The carry-over harness items remain visible but are not accidentally expanded into today's scope.
- The next session can continue from a clear handoff.

---

## 18. Handoff Requirement

`docs/ops/2026-04-10/DAILY_HANDOFF.md`
