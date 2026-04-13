# TODAY_STRATEGY

## 1. Date
2026-04-13

---

## 2. Strategy Objective
오늘은 Naver 뉴스 수집 실패를 가장 작은 안전 변경으로 복구할 수 있는 경로를 확정하고, 필요하면 provider 경계에서만 수정한다.  
batch log의 보관 위치 결정은 별도 운영 과제로 남기고, 오늘은 수집 복구와 회귀 방지에만 집중한다.

---

## 3. Current Context Summary
- scheduler와 admin-triggered ingestion은 같은 서버-side ingestion service 경로를 사용한다.
- 2026-04-13 기준 핵심 실패 경계는 selector나 controller가 아니라 Naver provider 쪽으로 다시 확인되었다.
- selector는 provider 결과를 집계하고 zero-result 요약을 남기는 downstream 단계다.
- 현재 QA는 서버 로그 직접 확인이 불안정하다고 말하지만, 그 문제는 오늘의 복구 수정 대상이 아니라 별도 운영 결정 대상이다.

---

## 4. Carry-over from Previous Session
- Naver 뉴스 수집 실패 복구
  - previous status: partial / deferred
  - why it was not completed: 지난 세션은 실패 경계 재확인까지 진행됐고, 실제 복구 코드는 아직 적용되지 않았다.
  - whether it is still relevant: yes
  - decision today: continue now
- batch log의 operator-accessible destination 결정
  - previous status: deferred
  - why it was not completed: 수집 복구와 분리된 운영 정책 이슈이며, 오늘의 최소 안전 변경 범위를 넓힌다.
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
- `docs/reports/2026-04-13-step-1-provider-failure-boundary-reconfirm.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-investigation.md`

---

## 6. User-Observed Issues
- Naver 뉴스 수집이 안정적으로 완료되지 않는다.
  - where it appears: admin-triggered batch ingestion와 scheduler 기반 ingestion
  - why it matters: 최신 뉴스가 들어오지 않으면 downstream interpretation과 freshness가 함께 나빠진다.
- 서버 로그를 직접 확인하기 어렵다.
  - where it appears: batch 실행 후 원인 확인 과정
  - why it matters: 실패 원인 파악 속도가 느려지고 복구 판단이 지연된다.

---

## 7. Code / System Findings
- `ScheduledNewsIngestionJob`과 `AdminNewsController`는 둘 다 같은 ingestion service 경로로 들어간다.
- `NewsIngestionServiceImpl`의 final freshness gate는 downstream 확인 지점이며, 최초 실패 경계는 아니다.
- `NewsSourceProviderSelector`는 provider 결과를 집계하고 zero-result summary를 기록한다.
- `NaverNewsSourceProvider`는 stale-date check, missing publish date, relevance filtering, unusable payload 제거를 담당한다.
- 2026-04-13 보고서는 failure boundary가 selector/controller가 아니라 Naver provider에 있음을 다시 확인했다.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: 사용자가 직접 체감하는 freshness 문제를 가장 빨리 복구할 수 있다.
  - scope: Naver provider의 최소 안전 복구, zero-result 방지, usable item 반환 경로 확인
- test coverage
  - why it exists: 같은 provider-side failure가 다시 zero-result로 떨어지는 회귀를 막아야 한다.
  - scope: provider/selector 경계의 최소 회귀 테스트 또는 검증 보강
- observability / operations
  - why it exists: 서버 로그 접근성이 낮아 실패 진단이 느려진다.
  - scope: batch log의 operator-accessible destination 결정

---

## 9. Priority Order
1. reliability
2. test coverage
3. observability / operations

---

## 10. Selection Logic
- reliability를 우선한 이유는 현재 QA의 핵심 증상이 실제 수집 실패이고, 보고서가 실패 경계를 provider로 좁혀 주었기 때문이다.
- test coverage를 함께 두는 이유는 provider-side 복구가 같은 입력 조건에서 다시 zero-result로 떨어지지 않는지 최소한 검증해야 하기 때문이다.
- observability / operations는 중요하지만, 오늘의 ingestion 복구를 unblock하지 않으며 scope를 넓히므로 defer 한다.
- `QA_STRUCTURED.md`와 `QA_INBOX.md`는 같은 문제를 가리키며, inbox는 인코딩이 깨져 보이지만 구조화된 입력과 의미상 차이는 없다.

---

## 11. Selected Work for Today
- bucket: reliability
  - goal: Naver provider의 failure boundary를 기준으로 최소 안전 복구 경로를 확정하고, usable item 반환을 되살린다.
  - why selected: 사용자 영향이 가장 크고 현재 가장 직접적인 장애 원인이다.
  - why not deferred: 오늘 처리하지 않으면 최신 뉴스 수집과 downstream interpretation이 계속 stale 상태로 남는다.
- bucket: test coverage
  - goal: 동일 입력 조건에서 provider-side fix가 다시 zero-result로 무너지는지 최소 회귀 검증을 확보한다.
  - why selected: recovery fix의 안전성을 확인해야 한다.
  - why not deferred: 코드만 바꾸고 검증이 없으면 같은 실패가 반복될 수 있다.

---

## 12. Step Breakdown

### Step 1. Reconfirm the Provider Failure Boundary

**Goal**
- Naver provider에서 usable item이 0개가 되는 정확한 조건을 다시 확인하고, 수정 지점을 provider 범위로 고정한다.

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
- batch log sink 정책 도입
- unrelated refactor

**Validation**
- 기존 코드 경로와 2026-04-13 보고서를 대조해 실패 경계가 provider인지 다시 확인한다.

**Expected Output**
- analysis note or verification report

### Step 2. Apply the Smallest Safe Recovery Fix

**Goal**
- 확인된 provider-side failure boundary 안에서만 최소 변경을 적용해 Naver 뉴스 수집을 복구한다.

**Target Area**
- provider / service

**Likely Files**
- Step 1에서 확정한 1~2개 파일

**Forbidden Scope**
- provider 전면 재설계
- selector/controller 로직 변경
- public API contract 변경
- broad refactor

**Validation**
- 동일 입력 조건에서 zero-result가 반복되지 않는지 확인한다.
- 정상/비정상 경로가 모두 기존 의도대로 유지되는지 확인한다.

**Expected Output**
- code change + minimal validation

### Step 3. Verify Regression Safety

**Goal**
- 복구 후에도 selector summary와 final freshness gate가 정상적으로 동작하는지 확인한다.

**Target Area**
- tests / reports

**Likely Files**
- 관련 테스트 파일
- 필요 시 `docs/reports/` 아래의 검증 기록

**Forbidden Scope**
- 문서 대규모 재작성
- 추가 기능 작업

**Validation**
- 대상 테스트 또는 최소 재현 시나리오로 회귀 여부를 확인한다.

**Expected Output**
- validation result

---

## 13. Recommended Agent Flow
기본 순서를 따른다.
1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

이번 작업은 분석 → 최소 수정 → 검증의 순서가 중요하므로, 순서를 바꾸지 않는다.

---

## 14. Codex Execution Notes
- Codex must read `PROJECT_BRIEF.md`, `AGENTS.md`, `HARNESS_RULES.md`, `DEV_LOOP.md`, and this strategy file.
- Work must stay inside `docs/ops/2026-04-13/` context plus the explicitly selected implementation files only.
- Codex must not create docs outside the ops date folder or mix ingestion recovery with log-sink policy work.
- Codex must validate before commit and report any mismatch between QA notes and the actual code path.

---

## 15. Risks and Constraints
- failure boundary가 provider 쪽으로 확인됐더라도, 실제 복구가 selector 또는 downstream gate의 부작용을 건드리지 않도록 해야 한다.
- provider 변경은 결과 분포를 바꿀 수 있으므로, 회귀 검증이 필요하다.
- raw QA inbox는 인코딩이 깨져 보이므로, 실행 판단은 구조화된 QA를 우선해야 한다.

---

## 16. Deferrals
- batch log의 operator-accessible destination 결정
  - reason: 오늘의 핵심 목표인 Naver 수집 복구를 unblock하지 않는다.
  - when to revisit: ingestion 복구가 끝난 뒤, 또는 서버 로그 접근성이 다시 진단 병목이 될 때

---

## 17. Definition of Done for Today
- Naver 뉴스 수집 복구 경로가 확인되거나, 최소 안전 변경이 적용된다.
- 대상 경로에 대한 최소 회귀 검증이 끝난다.
- unrelated file 변경이 없다.
- carry-over 항목이 명시적으로 처리되거나 재연기된다.
- 다음 세션이 이어서 실행 가능한 상태로 handoff 준비가 된다.

---

## 18. Handoff Requirement
오늘의 작업 종료 후 반드시 다음 파일을 생성한다.

`docs/ops/2026-04-13/DAILY_HANDOFF.md`

포함 항목:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
