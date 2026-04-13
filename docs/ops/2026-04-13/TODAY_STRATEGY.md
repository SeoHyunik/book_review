# TODAY_STRATEGY

## 1. Date
2026-04-13

---

## 2. Strategy Objective
오늘의 목적은 Naver 뉴스 수집 실패를 가장 작은 안전 변경으로 복구할 수 있는 실행 경로를 확정하고, Codex가 바로 수행할 수 있는 좁은 단계로 나누는 것입니다.  
동시에 batch log 저장소 결정은 오늘 범위에서 분리하여, 복구 작업과 운영 결정이 섞이지 않도록 유지합니다.

---

## 3. Current Context Summary
- 현재 ingestion 경로는 scheduler와 admin-trigger를 통해 진입하지만, 실제로는 Naver provider 단계에서 usable item이 0개가 되는 흐름이 재확인되었습니다.
- selector와 service는 최종 zero-result를 전달받는 downstream 지점이며, 1차 실패 경계는 Naver provider의 stale filtering, publish date 부족, relevance filtering, unusable payload 제거 쪽에 있습니다.
- `QA_STRUCTURED.md`는 오늘의 구현 우선순위를 ingestion 복구로 고정했고, log sink 결정은 별도의 운영 이슈로 분리했습니다.

---

## 4. Carry-over from Previous Session
- Naver 뉴스 수집 실패
  - previous status: partial / deferred
  - why it was not completed: 이전 세션에서는 실패 경계만 재확인했고, 실제 복구 변경은 적용하지 않았습니다.
  - whether it is still relevant: yes
  - decision today: continue now
- batch logs의 operator-accessible destination 결정
  - previous status: deferred
  - why it was not completed: 운영 경로 선택이 ingestion 복구와 분리되어야 하고, 오늘의 최소 안전 변경 범위를 넘습니다.
  - whether it is still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-13/QA_STRUCTURED.md`
- `docs/ops/2026-04-13/QA_INBOX.md`
- `docs/ops/2026-04-11/DAILY_HANDOFF.md`
- `docs/reports/2026-04-13-step-1-failure-path-reconfirm.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-investigation.md`

---

## 6. User-Observed Issues
- Naver 뉴스 수집이 현재 실패하고 있습니다.
  - where it appears: admin-triggered batch ingestion and server-side crawl path
  - why it matters: 최신 뉴스가 들어오지 않으면 downstream interpretation과 freshness가 즉시 약화됩니다.
- 서버 로그를 직접 확인하기 어려운 운영 상태가 진단을 늦추고 있습니다.
  - where it appears: batch log 확인 절차
  - why it matters: 실패 원인 확인이 어려워 복구와 재발 대응 속도가 떨어집니다.

---

## 7. Code / System Findings
- `ScheduledNewsIngestionJob`와 `AdminNewsController`는 같은 ingestion service로 진입만 담당합니다.
- `NewsIngestionServiceImpl`은 provider selector 결과를 수용하는 downstream 단계이며, 최종 zero-result의 직접 원인은 아닙니다.
- `NewsSourceProviderSelector`는 provider별 결과를 집계하고, 최종적으로 fresh/semi-fresh candidate가 비게 되면 empty selection으로 끝납니다.
- `NaverNewsSourceProvider` 쪽이 실제 failure boundary이며, stale filtering과 publish date/relevance 조건을 통과하지 못하면 usable item이 0개가 됩니다.
- `docs/reports/2026-04-13-step-1-failure-path-reconfirm.md`는 selector나 controller가 아니라 provider 경계가 첫 실패 지점임을 재확인했습니다.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: public freshness와 ingestion continuity가 직접적으로 영향을 받습니다.
  - scope: Naver provider의 최소 복구 수정, zero-result 재발 방지 확인
- observability / operations
  - why it exists: 실패 원인 확인이 느리면 복구와 재발 분석이 지연됩니다.
  - scope: batch log 저장소 결정 및 접근성 개선
- test coverage
  - why it exists: 동일한 provider 실패가 다시 발생해도 조기 탐지가 필요합니다.
  - scope: provider/selector 실패 경로를 최소 범위에서 검증

---

## 9. Priority Order
1. reliability
2. test coverage
3. observability / operations

---

## 10. Selection Logic
- carry-over 중 Naver 뉴스 수집 실패는 현재 사용자 영향이 직접적이고, 오늘 실제로 복구할 수 있는 유일한 실행 항목이므로 선택했습니다.
- batch log 저장소 결정은 중요하지만 운영 정책 성격이 강하고, ingestion 복구와 섞으면 변경 범위가 넓어집니다. 그래서 오늘은 제외했습니다.
- QA_STRUCTURED와 QA_INBOX는 같은 큰 이슈를 가리키지만, structured 문서가 우선순위와 분리를 더 명확하게 제시하므로 이를 primary input으로 사용했습니다.
- report 재확인은 failure boundary가 provider에 있다는 근거를 주므로, selector/controller 수정 같은 넓은 방향은 피하기로 했습니다.

---

## 11. Selected Work for Today
- bucket: reliability
  - goal: Naver provider의 failure boundary를 기준으로 최소 안전 수정 후보를 확정하고, 실제 수집이 usable item을 반환하도록 복구합니다.
  - why selected: 사용자가 직접 겪는 freshness 문제를 가장 빠르게 줄일 수 있습니다.
  - why not deferred: 오늘 미루면 최신 뉴스 공백이 계속 누적됩니다.
- bucket: test coverage
  - goal: 복구된 경로가 다시 zero-result로 떨어지지 않는지 최소 검증을 추가하거나 보강합니다.
  - why selected: 회귀 위험을 낮춰야 복구 변경을 안전하게 유지할 수 있습니다.
  - why not deferred: code fix만 하고 검증을 생략하면 같은 실패가 반복될 수 있습니다.

---

## 12. Step Breakdown

### Step 1. Reconfirm the Provider Failure Boundary

**Goal**
- Naver provider 내부에서 어떤 조건이 usable item을 0개로 만드는지 다시 확인하고, 최소 수정 지점을 확정합니다.

**Target Area**
- service / provider

**Likely Files**
- `src/main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java`
- `src/main/java/com/example/macronews/controller/AdminNewsController.java`
- `src/main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java`
- `src/main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java`
- `src/main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java`

**Forbidden Scope**
- selector ranking 재설계
- controller contract 변경
- batch log sink 도입
- unrelated refactor

**Validation**
- 코드 경로와 최근 리포트를 대조해 failure boundary를 provider로 고정합니다.

**Expected Output**
- verification note or analysis report

### Step 2. Apply the Smallest Safe Recovery Fix

**Goal**
- 확정된 failure boundary 안에서만 최소 변경을 적용해 Naver 수집이 다시 usable item을 반환하도록 복구합니다.

**Target Area**
- provider / service

**Likely Files**
- Step 1에서 확정한 1~2개 파일

**Forbidden Scope**
- provider 전체 재구성
- 새 기능 추가
- API contract 변경
- 넓은 리팩토링

**Validation**
- 동일 입력 조건에서 zero-result가 반복되지 않는지 확인합니다.

**Expected Output**
- code change + 최소 검증

### Step 3. Verify Regression Safety

**Goal**
- 복구 후에도 selector와 final summary가 정상적으로 이어지는지 확인합니다.

**Target Area**
- tests / reports

**Likely Files**
- 관련 테스트 파일
- 필요 시 `docs/reports/`의 짧은 검증 기록

**Forbidden Scope**
- 문서 대규모 개편
- 추가 기능 작업

**Validation**
- 선택 경로가 다시 empty selection으로 떨어지지 않는지 확인합니다.

**Expected Output**
- validation result

---

## 13. Recommended Agent Flow
기본 순서를 유지합니다.
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

이번 작업은 순차 분석과 최소 수정이 중요하므로 병렬화하지 않습니다.

---

## 14. Codex Execution Notes
- Codex must read `PROJECT_BRIEF.md`, `AGENTS.md`, `HARNESS_RULES.md`, `DEV_LOOP.md`, and this strategy file.
- Work must stay inside `docs/ops/2026-04-13/` context and the explicitly selected implementation files only.
- Codex must not create docs outside the ops date folder or mix ingestion repair with log-sink policy work.
- Codex must validate before commit and report any mismatch between QA notes and the actual code path.

---

## 15. Risks and Constraints
- 실제 failure boundary가 provider 내부의 필터 조건에 걸려 있으므로, 수정 범위를 잘못 잡으면 selector나 controller까지 불필요하게 건드릴 위험이 있습니다.
- log sink 결정은 오늘 보류하지만, 운영 진단 필요성은 남아 있으므로 다음 세션에서 별도 이슈로 다시 다뤄야 합니다.
- raw QA inbox에는 인코딩이 깨진 문장이 있어, 구체 문구보다 구조화 문서를 우선해야 합니다.

---

## 16. Deferrals
- batch logs의 operator-accessible destination 결정
  - reason: ingestion 복구와 분리된 운영 결정입니다.
  - when to revisit: Naver 수집 복구가 안정화된 뒤

---

## 17. Definition of Done for Today
- Naver 뉴스 수집 실패의 최소 안전 복구 방향이 코드 경로 기준으로 확정됩니다.
- 필요한 경우 작은 코드 수정과 최소 검증이 완료됩니다.
- unrelated file 수정 없이 작업이 끝납니다.
- carry-over와 보류 항목이 명시적으로 정리됩니다.
- 다음 세션이 이어받을 수 있는 상태로 handoff 준비가 가능해집니다.

---

## 18. Handoff Requirement
오늘 작업 종료 시 반드시 다음 파일을 생성합니다.

`docs/ops/2026-04-13/DAILY_HANDOFF.md`

포함 항목:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
