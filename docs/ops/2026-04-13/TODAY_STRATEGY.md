# TODAY_STRATEGY

## 1. Date
2026-04-13

---

## 2. Strategy Objective
오늘의 목적은 Naver 뉴스 수집 실패를 가장 작은 안전 변경으로 복구하는 것입니다.
동시에 배치 로그의 외부 접근성 문제는 별도 제품 결정으로 분리해서, 복구 작업과 섞지 않습니다.

---

## 3. Current Context Summary
- 현재 배치 수집 경로와 admin 진입점은 동작하지만, Naver 경로에서 최종적으로 새 뉴스가 0건으로 수렴하고 있습니다.
- 로그상 Naver는 stale filtering으로 모두 제거되고, NewsAPI는 429, GNews는 400으로 떨어져 결과적으로 선택 가능한 후보가 비게 됩니다.
- `docs/ops/2026-04-11/DAILY_HANDOFF.md`는 비어 있어서, 실제 carry-over는 오늘 QA와 보고서로 재구성해야 합니다.

---

## 4. Carry-over from Previous Session
- `docs/ops/2026-04-11/DAILY_HANDOFF.md`에 명시된 unfinished item은 없었습니다. 따라서 아래 항목은 오늘 QA와 보고서에서 재구성한 carry-over 후보입니다.
- Naver 뉴스 수집 실패
  - previous status: deferred/partial
  - why it was not completed: 이전 세션에서 최종 복구까지 닫히지 않았고, handoff가 비어 있어 실행 가능 상태가 남지 않았습니다.
  - whether it is still relevant: yes
  - decision today: continue now
- 배치 로그 외부 접근성 결정
  - previous status: deferred
  - why it was not completed: 복구와 무관한 별도 운영/제품 결정입니다.
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
- `docs/reports/2026-04-06-admin-auto-ingestion-investigation.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- `docs/reports/2026-04-06-admin-auto-ingestion-log-with-explanations.md`
- `docs/reports/2026-04-07-step-1-admin-auto-ingestion-trace-points.md`
- `docs/reports/2026-04-07-step-2-re-review-and-handoff-check.md`
- `docs/reports/2026-04-08-step-2-daily-ops-continuity-check.md`

---

## 6. User-Observed Issues
- Naver 뉴스 수집이 현재 실패하고 있습니다.
  - where it appears: admin 자동 수집 경로와 배치 수집 결과
  - why it matters: 최신 뉴스가 들어오지 않으면 이후 해석 결과와 요약 품질이 동시에 떨어집니다.
- 배치 로그를 운영자가 쉽게 확인하기 어렵습니다.
  - where it appears: 수집 실패 후 원인 확인 흐름
  - why it matters: 재발 시 root-cause 분석 속도가 느려집니다.

---

## 7. Code / System Findings
- 배치 실행은 scheduler와 selector까지 정상 진입하지만, 최종 후보 수가 0으로 수렴합니다.
- Naver provider는 raw item은 받지만 stale filtering 이후 parsed usable item이 0이 됩니다.
- NewsAPI는 429 rate limit, GNews는 400 bad request로 떨어져 대체 경로도 비게 됩니다.
- 결과적으로 `freshCandidates=0`, `semiFreshCandidates=0`, `selected sourceSummary={}` 상태가 발생합니다.
- zero-result 경로를 한 줄로 설명하는 운영자용 최종 요약 로그는 여전히 부족합니다. 다만 오늘의 주요 범위는 로그 sink 설계가 아니라 수집 복구입니다.

---

## 8. Candidate Work Buckets
- reliability
  - why it exists: public freshness가 직접 깨지고 있습니다.
  - scope: Naver 수집 실패의 최소 원인 복구, 관련 경로의 작은 수정, 좁은 회귀 검증
- observability / operations
  - why it exists: 실패 후 원인 확인이 느립니다.
  - scope: 배치 로그를 Email, Google Drive, 또는 날짜별 GitHub 디렉터리 중 어디에 둘지 결정
- test coverage
  - why it exists: 복구가 재발 없이 유지되어야 합니다.
  - scope: 복구 경로에 대한 좁은 회귀 테스트 또는 검증 보강

---

## 9. Priority Order
1. reliability
2. test coverage
3. observability / operations

---

## 10. Selection Logic
- carry-over 중 Naver 뉴스 수집 실패는 현재 사용자 영향이 가장 크고, 수집 품질과 하위 해석 품질을 동시에 막고 있으므로 오늘 선택합니다.
- 배치 로그 외부 접근성 결정은 중요하지만, 복구를 위해 오늘 반드시 해결해야 하는 선행 조건은 아닙니다.
- QA_STRUCTURED와 QA_INBOX는 실질적으로 같은 두 문제를 가리키며, material mismatch는 없습니다.
- trade-off는 명확합니다. 오늘은 한 문제를 확실히 닫고, 운영 로그 sink 논의는 다음 세션으로 미룹니다.

---

## 11. Selected Work for Today
- bucket: reliability
  - goal: Naver 뉴스 수집이 다시 유효한 새 항목을 반환하도록 최소 원인을 복구합니다.
  - why selected: 사용자 체감 영향이 가장 크고, 현재 공백 상태를 직접 해소합니다.
  - why not deferred: 더 미루면 public freshness와 downstream interpretation이 계속 비어 있게 됩니다.
- bucket: test coverage
  - goal: 복구가 재발하지 않도록 좁은 검증을 추가하거나 기존 검증으로 확인합니다.
  - why selected: 복구 후 회귀 방지가 필요합니다.
  - why not deferred: 검증 없이 복구만 하면 다시 같은 실패가 반복될 수 있습니다.

---

## 12. Step Breakdown

### Step 1. Failure Path Reconfirm

**Goal**
- 실제 실패 경로를 다시 확인하고, Naver 수집이 0건이 되는 지점을 한 단계로 고정합니다.

**Target Area**
- service / provider

**Likely Files**
- `src/main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java`
- `src/main/java/com/example/macronews/controller/AdminNewsController.java`
- `src/main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java`
- `src/main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java`
- `src/main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java`

**Forbidden Scope**
- freshness threshold 변경
- provider 범위 확장
- API 계약 변경
- 배치 로그 sink 재설계

**Validation**
- 현재 로그와 코드 경로를 대조해서 실패 경계를 다시 확인합니다.

**Expected Output**
- analysis note or small verification report

### Step 2. Minimal Recovery Fix

**Goal**
- 확인된 단일 원인만 수정해서 Naver 수집이 다시 살아나게 합니다.

**Target Area**
- provider / service

**Likely Files**
- Step 1에서 확정된 1~2개 소스 파일

**Forbidden Scope**
- selector 전체 리팩터링
- 불필요한 로깅 확장
- 새로운 기능 추가
- 다른 provider 동작 변경

**Validation**
- 동일 조건에서 Naver 경로가 0건으로 끝나지 않는지 확인합니다.

**Expected Output**
- code + 좁은 회귀 검증

### Step 3. Post-fix Verification

**Goal**
- 복구 후 영향 범위를 다시 확인하고, 다른 경로가 깨지지 않았는지 검증합니다.

**Target Area**
- tests / reports

**Likely Files**
- 관련 테스트 파일
- 필요한 경우 `docs/reports/` 아래의 짧은 검증 रिपोर्ट

**Forbidden Scope**
- 무관한 문서 수정
- 추가 기능 작업

**Validation**
- 빌드 또는 최소 회귀 검증
- 로그/출력 확인

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

이번 작업은 범위가 좁아서 순서를 바꿀 필요가 없습니다.

---

## 14. Codex Execution Notes
- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `HARNESS_RULES.md`
- `DEV_LOOP.md`
- 이 strategy 파일
- `docs/ops/2026-04-13/` 폴더만 사용
- 다른 날짜의 ops 파일이나 unrelated 파일은 수정하지 않음
- 변경 전후로 검증을 명시하고, commit 전에 결과를 확인함

---

## 15. Risks and Constraints
- 실제 원인이 provider 설정, freshness gate, upstream 응답, 또는 조합 문제일 수 있습니다.
- 범위를 넓히면 로그 sink 결정까지 같이 묶이기 쉬우므로 scope creep을 조심해야 합니다.
- 테스트가 부족하면 복구가 재발할 위험이 있습니다.
- 기존 Korean 텍스트와 운영 문구는 보존해야 합니다.

---

## 16. Deferrals
- 배치 로그 외부 접근성 결정
  - reason: 복구와 별개의 운영/제품 결정입니다.
  - when to revisit: Naver 수집 복구가 끝난 뒤
- 광범위한 freshness 정책 재설계
  - reason: 오늘 목표는 복구이고, 정책 재설계는 범위를 크게 키웁니다.
  - when to revisit: 복구 후 추가로 구조적 한계가 확인될 때

---

## 17. Definition of Done for Today
- Naver 뉴스 수집 실패의 최소 원인이 복구되었습니다.
- 불필요한 변경 없이 검증이 끝났습니다.
- carry-over가 명확하게 정리되었습니다.
- 남은 위험과 다음 세션의 출발점이 문서화되었습니다.

---

## 18. Handoff Requirement
오늘 작업 종료 시 반드시 다음 파일을 생성합니다.

`docs/ops/2026-04-13/DAILY_HANDOFF.md`

여기에는 다음이 포함되어야 합니다.
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
