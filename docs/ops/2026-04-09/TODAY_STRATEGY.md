# TODAY_STRATEGY

## 1. Date
2026-04-09

---

## 2. Strategy Objective
오늘은 두 가지 P1 항목을 작게 정리한다. 하나는 admin 자동 수집의 zero-result 경로를 운영자가 읽을 수 있게 만드는 것이고, 다른 하나는 QA/전략/핸드오프가 같은 미해결 상태를 유지하도록 continuity gate를 명확히 하는 것이다.

---

## 3. Current Context Summary
- admin 자동 수집은 아직 `parsedItems=0`, `freshCandidates=0`, `selected sourceSummary={}`, `returned=0`, `analyzed=0` 같은 결과만 남고, 한 번에 읽히는 최종 원인이 부족하다.
- 2026-04-08의 handoff는 carry-over 항목을 정리했지만, 재사용 가능한 다음 세션 상태로 완전히 닫히지는 못했다.
- `QA_STRUCTURED.md`는 오늘의 실행 우선순위를 이미 두 개의 P1 항목으로 정규화했다.
- `QA_INBOX.md`는 같은 carry-over를 다시 적은 수준이며, 추가적인 실행 가능한 새 이슈는 없다.
- inbox에는 raw text 인코딩/형식 손상 징후가 있어, structured 문서를 기준으로 삼는 편이 안전하다.

---

## 4. Carry-over from Previous Session
- Admin auto-ingestion zero-result diagnostics
  - previous status: partial
  - why it was not completed: 이전 세션은 trace path와 최소 수정 지점을 확인하는 분석까지만 끝났고, 실제 코드 변경은 하지 않았다.
  - still relevant: yes
  - decision today: continue now
- Reusable handoff completion gate / workday-state enforcement
  - previous status: blocked
  - why it was not completed: handoff 파일 자체가 비어 있거나 재사용 가능한 상태 경계가 없어서, 세션 종료를 검증하는 장치가 부족했다.
  - still relevant: yes
  - decision today: continue now
- Final freshness gate policy
  - previous status: deferred
  - why it was not completed: 이것은 진단 가시성보다 상위의 product decision 이슈라서, 먼저 zero-result 원인부터 분리해야 했다.
  - still relevant: yes
  - decision today: defer again
- Public page follow-up scope
  - previous status: deferred
  - why it was not completed: admin ingest 안정화보다 우선순위가 낮고, 다음 사용자-facing 범위가 아직 정의되지 않았다.
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-09/QA_STRUCTURED.md`
- `docs/ops/2026-04-09/QA_INBOX.md`
- `docs/ops/2026-04-08/DAILY_HANDOFF.md`
- `docs/reports/2026-04-07-step-1-admin-auto-ingestion-trace-points.md`
- `docs/reports/2026-04-08-step-2-daily-ops-continuity-check.md`
- `docs/reports/2026-04-07-step-2-re-review-and-handoff-check.md`

---

## 6. User-Observed Issues
- symptom: admin 자동 수집이 zero-result로 끝나도 운영자가 즉시 읽을 수 있는 단일 이유가 없다
  - where it appears: admin ingestion logs, selector summary, final freshness path
  - why it matters: triage가 길어지고, 정상 실패인지 비정상 실패인지 빠르게 구분하기 어렵다
- symptom: 세션 종료 후 다음 날이 같은 carry-over를 다시 복원해야 한다
  - where it appears: QA/strategy/handoff chain
  - why it matters: 반복 작업이 늘고, 실제 미해결 작업과 문서 복원 작업이 섞인다

---

## 7. Code / System Findings
- trace report confirms the zero-result path spans scheduler entry, admin entry, provider selection, and final freshness filtering, so one log line alone는 충분하지 않다.
- provider-level empty outcomes and selector-level empty outcomes are already distinguishable in the trace; the gap is the final operator-facing summary.
- the previous handoff shows an unresolved continuity gap because the reusable handoff state boundary was not closed.
- QA_INBOX does not materially disagree with QA_STRUCTURED; it only adds raw reminder text and shows formatting/encoding noise.
- the old freshness gate question remains a product decision, not a prerequisite for the diagnostics fix.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: zero-result ingestion runs need a single traceable explanation for operators
  - scope: add reason-bearing logs or summary markers in the admin ingestion path, selector, provider-empty cases, and final freshness gate
- process / harness
  - why it exists: the workday needs a reusable boundary so unfinished work does not have to be reconstructed manually
  - scope: enforce continuity checks across QA, strategy, handoff, and any workday-state indicator used by the harness
- product decision
  - why it exists: the final freshness gate behavior is still unresolved as a policy question
  - scope: defer or redesign the gate only after the diagnostics path is clearer
- public follow-up
  - why it exists: there is still an open user-facing follow-up after ingest stability work
  - scope: define the next public page or UX step without widening today's execution

---

## 9. Priority Order
1. reliability
2. process / harness
3. product decision
4. public follow-up

---

## 10. Selection Logic
- carry-over item 1 stays selected because it is still a P1 reliability gap and the smallest safe change surface is now known from the trace reports.
- carry-over item 2 stays selected because the current planning chain still ends without a reusable closure point, which keeps the next session noisy and error-prone.
- the freshness gate policy is explicitly deferred because it is still a product decision and does not block the diagnostics work.
- the public follow-up is deferred because it is lower leverage than fixing operator visibility and session continuity.
- QA_INBOX did not change the priority order; it only confirmed that no new actionable issue displaced the structured list.

---

## 11. Selected Work for Today
- reliability
  - goal: make zero-result admin ingestion runs explain themselves with one traceable summary and supporting reason-bearing logs
  - why selected: it is the highest-impact operational gap and has a clear minimal edit surface
  - why not deferred: continuing to ship zero-result ambiguity will keep triage slow and noisy
- process / harness
  - goal: enforce a reusable continuity boundary so QA, strategy, and handoff preserve the same unresolved work state
  - why selected: today still lacks a clean end-of-day state and the next session should not rebuild context from scratch
  - why not deferred: the issue is already affecting the planning loop, not just future documentation quality

---

## 12. Step Breakdown

### Step 1. Add operator-readable zero-result diagnostics
**Goal**
- Produce one clear end-state explanation for admin auto-ingestion zero-result runs without changing ingestion behavior.

**Target Area**
- service / provider / repository-free logging path

**Likely Files**
- `src/main/java/.../AdminNewsController.java`
- `src/main/java/.../NewsIngestionServiceImpl.java`
- `src/main/java/.../NewsSourceProviderSelector.java`
- provider implementations that already emit empty-result outcomes

**Forbidden Scope**
- do not change freshness thresholds
- do not change provider selection behavior
- do not add new public API contracts
- do not refactor unrelated ingestion code

**Validation**
- inspect logs for a run that previously ended in `selected sourceSummary={}` and confirm the final summary now states why it was empty
- run targeted tests only if existing coverage already exercises the same ingestion path

**Expected Output**
- code
- targeted verification notes

### Step 2. Make continuity state explicit for the day boundary
**Goal**
- Ensure QA, strategy, and handoff preserve the same unresolved work state and flag missing or corrupted continuity early.

**Target Area**
- docs / harness / workday-state boundary

**Likely Files**
- `docs/ops/2026-04-09/DAILY_HANDOFF.md`
- `docs/ops/HARNESS_FAILURES.md`
- any existing workday-state file used by the harness

**Forbidden Scope**
- do not broaden scope into product-policy changes
- do not rewrite unrelated ops history
- do not mix in the freshness-gate decision

**Validation**
- confirm the selected carry-over items appear consistently across QA_STRUCTURED, TODAY_STRATEGY, and the resulting handoff
- verify the handoff closes the continuity gap without inventing new work

**Expected Output**
- doc
- harness note

---

## 13. Recommended Agent Flow
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

This is the default flow and is sufficient for both selected items.

---

## 14. Codex Execution Notes
- read:
  - `PROJECT_BRIEF.md`
  - `AGENTS.md`
  - `HARNESS_RULES.md`
  - `DEV_LOOP.md`
  - this strategy file
- use:
  - only `docs/ops/2026-04-09/` for daily ops outputs
- must not:
  - create daily docs outside the date folder
  - modify unrelated files
  - mix the selected work with freshness-policy work
- must:
  - execute the two selected steps in order of operational risk
  - validate before committing any future implementation

---

## 15. Risks and Constraints
- The zero-result path may span several components, so the diagnostic change must stay minimal or it will spread too far.
- The continuity gate can drift into harness design if we are not strict about the smallest useful check.
- QA_INBOX has low fidelity and some encoding noise, so it should remain a cross-check only.
- The handoff/state boundary may already have a separate mechanism, and we should not overwrite it blindly.

---

## 16. Deferrals
- Final freshness gate policy
  - reason: product decision, not required to unblock diagnostics
  - revisit: after the zero-result path is explainable
- Public page follow-up scope
  - reason: lower priority than operational stability
  - revisit: after the reliability and continuity items are closed

---

## 17. Definition of Done for Today
- the zero-result admin ingestion path has a traceable explanation
- continuity between QA, strategy, and handoff is explicitly preserved
- no unrelated files were pulled into the plan
- deferred policy work remains deferred on purpose
- the next session can start from a clear state

---

## 18. Handoff Requirement
At end of work, MUST generate:

`docs/ops/2026-04-09/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
