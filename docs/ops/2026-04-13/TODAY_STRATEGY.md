# TODAY_STRATEGY

## 1. Date
2026-04-13

---

## 2. Strategy Objective
오늘의 목적은 Naver 뉴스 수집 실패를 가장 작은 안전 변경으로 복구하는 것입니다.
동시에, 진단을 어렵게 만드는 배치 로그 접근성 문제는 별도 결정 항목으로 분리해 오늘 실행 범위가 불필요하게 커지지 않도록 합니다.

---

## 3. Current Context Summary
- 현재 수집 경로는 scheduler와 admin-trigger 모두 존재하지만, 실제 배치 결과는 Naver 쪽에서 usable item이 0건으로 수렴하고 있습니다.
- `NewsSourceProviderSelector`와 `NaverNewsSourceProvider`의 흐름상, 최종 빈 결과는 provider parsing/filtering 또는 provider selection 단계에서 이미 확정되고 있습니다.
- `QA_STRUCTURED.md`는 오늘의 구현 우선순위를 ingestion 복구로 정리했고, 로그 저장처 결정은 별도 운영 의사결정으로 분리했습니다.

---

## 4. Carry-over from Previous Session
- Naver 뉴스 수집 실패
  - previous status: deferred / partial
  - why it was not completed: 이전 세션에서는 실패 경로 재확인만 진행했고, 안전한 복구 변경은 아직 적용하지 않았습니다.
  - whether it is still relevant: yes
  - decision today: continue now
- 배치 로그를 어디에 남길지에 대한 운영 결정
  - previous status: deferred
  - why it was not completed: 로그 저장소 선택은 복구 자체와 분리된 운영 결정이기 때문입니다.
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
  - where it appears: admin-trigger 및 batch ingestion 경로의 Naver source 처리
  - why it matters: 최신 뉴스가 쌓이지 않으면 downstream 해석과 시장 브리핑 품질이 바로 약화됩니다.
- 서버 로그를 바로 확인하기 어려워 수집 실패 원인 파악이 지연되고 있습니다.
  - where it appears: 배치 운영과 로그 조회 흐름
  - why it matters: 복구 판단이 느려지고, 동일 장애의 재발 진단도 늦어집니다.

---

## 7. Code / System Findings
- `ScheduledNewsIngestionJob`와 `AdminNewsController`는 둘 다 동일한 ingestion service로 진입합니다.
- `NewsIngestionServiceImpl`의 batch path는 provider selector 결과에 의존하며, selector가 비어 있으면 최종 processed count는 0으로 끝납니다.
- `NewsSourceProviderSelector`는 fresh candidate가 없을 때 semi-fresh 및 fallback 흐름을 거치지만, 최근 실 로그에서는 최종 selection이 empty로 수렴했습니다.
- `NaverNewsSourceProvider`는 raw item을 받더라도 stale filtering 이후 usable item이 0건이 될 수 있습니다.
- 오늘 기준으로는 final service summary를 고치는 문제가 아니라, upstream provider parsing/filtering 또는 selection recovery를 최소 범위로 복구하는 문제가 핵심입니다.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: public freshness가 직접 손상되고 있습니다.
  - scope: Naver 수집 실패 원인 재확인, 최소 복구 수정, 재발 방지 확인
- observability / operations
  - why it exists: 장애 원인 확인 속도가 느립니다.
  - scope: batch 로그 저장처 결정, 운영자가 다시 확인할 수 있는 접근 경로 정리
- test coverage
  - why it exists: 복구 후 같은 경로가 다시 깨지지 않도록 검증이 필요합니다.
  - scope: selector/provider 경로의 최소 회귀 검증

---

## 9. Priority Order
1. reliability
2. test coverage
3. observability / operations

---

## 10. Selection Logic
- carry-over 중 Naver 뉴스 수집 실패는 현재 사용자 영향이 가장 크고, 실제로 오늘 복구 가능한 범위에 들어 있으므로 선택했습니다.
- 배치 로그 저장처 결정은 진단 품질에는 중요하지만, ingestion 복구와는 분리된 운영 의사결정이므로 오늘의 실행 범위에서는 제외했습니다.
- QA_STRUCTURED와 QA_INBOX는 의미상 동일하며, inbox의 원문은 깨진 표기만 있을 뿐 추가적인 다른 요구는 없습니다.
- 따라서 오늘은 복구 우선, 운영 의사결정은 후속으로 미루는 구성이 가장 안전합니다.

---

## 11. Selected Work for Today
- bucket: reliability
  - goal: Naver 뉴스 수집 실패의 실제 원인을 재확인하고, usable item이 0건으로 수렴하는 지점을 최소 변경으로 복구합니다.
  - why selected: 가장 직접적인 사용자 영향이 있고, 오늘의 실행 가능 범위 안에 있습니다.
  - why not deferred: 복구를 미루면 최신성 저하와 downstream 해석 품질 저하가 계속됩니다.
- bucket: test coverage
  - goal: 복구한 경로가 다시 zero-result로 돌아가지 않도록 최소 회귀 검증을 추가하거나 보강합니다.
  - why selected: 복구 직후의 안전성 확인이 필요합니다.
  - why not deferred: 검증 없이 변경만 하면 같은 실패가 반복될 수 있습니다.

---

## 12. Step Breakdown

### Step 1. Reconfirm Failure Boundary

**Goal**
- Naver 수집 실패가 selector 단계인지 provider parsing/filtering 단계인지 다시 한 번 좁혀서, 수정 지점을 한 곳으로 고정합니다.

**Target Area**
- service / provider

**Likely Files**
- `src/main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java`
- `src/main/java/com/example/macronews/controller/AdminNewsController.java`
- `src/main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java`
- `src/main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java`
- `src/main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java`

**Forbidden Scope**
- freshness threshold 조정
- provider ranking 전면 개편
- batch log sink 도입
- unrelated refactor

**Validation**
- 현재 코드와 최근 실 로그를 대조해 failure boundary를 한 지점으로 특정합니다.

**Expected Output**
- small analysis note or verification report

### Step 2. Minimal Recovery Fix

**Goal**
- 확인된 단일 원인만 수정해 Naver 수집이 다시 usable item을 반환하도록 만듭니다.

**Target Area**
- provider / service

**Likely Files**
- Step 1에서 원인으로 특정된 1~2개 파일

**Forbidden Scope**
- selector 전체 재설계
- API contract 변경
- 다른 provider 동작 변경
- 새로운 기능 추가

**Validation**
- 동일 입력 조건에서 Naver selection이 0건으로 끝나지 않는지 확인합니다.

**Expected Output**
- code change + 최소 검증

### Step 3. Post-fix Verification

**Goal**
- 복구 후에도 하위 경로와 최종 summary가 정상적으로 이어지는지 확인합니다.

**Target Area**
- tests / reports

**Likely Files**
- 관련 테스트 파일
- 필요 시 `docs/reports/`의 짧은 검증 기록

**Forbidden Scope**
- 문서 대규모 개편
- 추가 기능 작업

**Validation**
- 선택된 경로가 다시 empty selection으로 수렴하지 않는지 확인합니다.

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

이번 작업은 독립적인 보조 작업이 부족하므로 순차 흐름이 가장 안전합니다.

---

## 14. Codex Execution Notes
- Codex는 반드시 `PROJECT_BRIEF.md`, `AGENTS.md`, `HARNESS_RULES.md`, `DEV_LOOP.md`, 그리고 이 전략 파일을 함께 읽어야 합니다.
- 작업은 `docs/ops/2026-04-13/` 범위 안에서만 진행합니다.
- 다른 ops 문서나 unrelated 파일은 수정하지 않습니다.
- 변경 전후에는 verification을 남기고, commit 전에 결과를 확인해야 합니다.

---

## 15. Risks and Constraints
- 실제 원인이 provider filtering인지 selector fallback인지 아직 완전히 단정되지는 않았습니다.
- 복구 범위를 넓히면 다른 provider 동작까지 건드릴 위험이 있습니다.
- 로그 접근성 문제는 남아 있으므로, 추후 디버깅 속도는 제한될 수 있습니다.
- 한국어 user-facing 문구와 기존 코멘트는 불필요하게 변경하지 않아야 합니다.

---

## 16. Deferrals
- 배치 로그 저장처 결정
  - reason: 오늘의 핵심 목표는 ingestion 복구이며, 로그 sink 결정은 별도 운영 과제입니다.
  - when to revisit: 복구가 안정화된 뒤 또는 운영/디버깅 병목이 다시 확인될 때

---

## 17. Definition of Done for Today
- Naver 뉴스 수집 실패의 최소 원인이 확인되었습니다.
- 최소 안전 변경으로 복구 경로가 적용되었습니다.
- 회귀 검증이 완료되었습니다.
- unrelated file 변경 없이 작업이 끝났습니다.
- 다음 세션에서 이어질 수 있도록 carry-over와 위험이 정리되었습니다.

---

## 18. Handoff Requirement
오늘 작업 종료 시 반드시 다음 파일을 생성해야 합니다.

`docs/ops/2026-04-13/DAILY_HANDOFF.md`

포함 항목:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
