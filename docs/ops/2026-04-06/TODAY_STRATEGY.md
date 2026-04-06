# TODAY_STRATEGY

## 1. Date

2026-04-06

---

## 2. Strategy Objective

오늘의 목표는 공개 페이지에서 사용자가 가장 먼저 체감하는 마찰을 작고 안전한 범위로 줄이는 것이다.
특히 "클릭하면 전체 페이지가 새로고침되는 느낌"과 "메인 화면이 너무 밋밋한 느낌"을 하나의 좁은 실행 단위로 정리하고, 대규모 리팩터링은 피한다.

---

## 3. Current Context Summary

- Spring Boot + Thymeleaf 모놀리스 방향은 유지되고 있다.
- 2026-04-03 세션에서 query noise tuning 은 부분 완료 상태로 남아 있다.
- 오늘 QA에서는 공개 UI의 정적 느낌, 기본 언어가 영문으로 시작되는 문제, 일부 클릭 동작의 전체 새로고침 체감이 새로 확인되었다.
- `docs/reports/` 에는 오늘 계획에 직접 쓸 수 있는 최근 보고서가 보이지 않는다.

---

## 4. Carry-over from Previous Session

- Query noise tuning
  - previous status: partial
  - why it was not completed: provider query 범위만 좁힌 상태였고, selector / ranking / UI / AI summary 쪽은 건드리지 않았다.
  - still relevant: yes
  - decision today: defer again

- AI Summary Korean Tone Cleanup
  - previous status: deferred
  - why it was not completed: query tuning 과 분리된 후속 작업으로 남겨 두었다.
  - still relevant: yes
  - decision today: defer again

- SEO Foundation Minimal Pass
  - previous status: deferred
  - why it was not completed: public route 메타 정리와 콘텐츠 구조 보강이 별도 단계로 필요하다.
  - still relevant: yes
  - decision today: defer again

- Retention Policy Decision
  - previous status: deferred
  - why it was not completed: today-only / archive / delete 정책은 제품 방향 결정을 더 필요로 한다.
  - still relevant: yes
  - decision today: defer again

- Admin Usage Follow-up
  - previous status: deferred
  - why it was not completed: 사용량 집계와 비용 정합성은 별도 검증이 필요한 운영 이슈다.
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning

- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/2026-04-06/QA_INBOX.md`
- `docs/ops/2026-04-06/QA_STRUCTURED.md`
- `docs/ops/2026-04-03/DAILY_HANDOFF.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/reports/` 현황
- 오늘 사용자 QA 메모

---

## 6. User-Observed Issues

- 메인 화면에서 테이블 좌측 여백이 부족하고 source 표기가 너무 단순하다.
  - where: 메인페이지 하단 뉴스 테이블
  - why it matters: 가독성이 떨어지고 신뢰도도 낮아 보인다.

- 클릭 동작 하나에 전체 페이지가 새로고침되는 체감이 있다.
  - where: 공개 UI 전반
  - why it matters: 사용자가 React/Vue 수준의 반응성을 기대하는데 현재 흐름은 거슬린다.

- 최초 진입 시 기본 언어가 영문으로 시작된다.
  - where: 첫 진입 언어 상태
  - why it matters: 한국어 서비스 정체성과 바로 충돌한다.

- AI 시장 요약 상세페이지가 밋밋하고 기계적으로 느껴진다.
  - where: AI market summary detail page
  - why it matters: 제품의 핵심 체감 가치가 약해진다.

- 아카이브 페이지가 밋밋하고 중복 제목/페이징 문제가 있다.
  - where: archive page
  - why it matters: 탐색성과 재방문성이 떨어진다.

- 버튼과 헤더/푸터가 허술하게 보인다.
  - where: header, footer, action buttons
  - why it matters: 전체 품질 인식에 바로 영향을 준다.

- 자동 수집 / 최신뉴스 가져오기 기능이 멈춘 것으로 의심된다.
  - where: admin auto-collection flow
  - why it matters: 신규 콘텐츠 공급이 막히면 서비스 품질이 연쇄적으로 떨어진다.

---

## 7. Code / System Findings

- 이전 세션의 작업은 provider query 범위만 좁힌 bounded change 였고, 다른 계층으로 확장하지 않도록 정리되어 있었다.
- 이번 QA는 하나의 버그가 아니라 최소 3개의 서로 다른 축을 보여 준다: public rendering, locale/default state, content generation tone.
- 오늘은 아직 코드 레벨 root cause 를 확인하지 않았으므로, 먼저 실행 경로를 추적한 뒤에만 편집 범위를 확정해야 한다.
- `docs/reports/` 에서 바로 인용할 수 있는 최신 리포트가 보이지 않아, 이번 전략은 QA + 직전 handoff 중심으로 수립했다.
- 제품 방향상 Spring Boot + Thymeleaf 모놀리스는 유지해야 하므로, UI 개선도 부분 업데이트 가능 범위 안에서만 접근해야 한다.

---

## 8. Candidate Work Buckets

- reliability
  - why it exists: 사용자 체감이 가장 큰 "전체 새로고침" 문제를 줄여야 한다.
  - scope: 한 개의 대표 클릭 경로와 그에 딸린 템플릿/컨트롤러 흐름만 좁게 점검한다.

- UX/UI polish
  - why it exists: 메인 뉴스 테이블, 버튼, header/footer, archive page 가 모두 거칠게 느껴진다.
  - scope: 같은 화면 단위 안에서 padding, source label, 라운드 버튼 품질, 기본 문구를 정리한다.

- localization/content tone
  - why it exists: 한국어 서비스인데 최초 언어와 AI 문체가 어색하다.
  - scope: 기본 언어 상태와 AI summary 문체를 분리해서 정리한다.

- query/ranking validation
  - why it exists: 지난 세션의 query tuning 이 부분 완료 상태로 남아 있다.
  - scope: provider query, fallback, ranking 결과가 실제 recall 을 해치지 않는지 검증한다.

- ops/data hygiene
  - why it exists: archive, retention, admin usage, auto-collection 문제는 운영 신뢰성과 직결된다.
  - scope: 당일에는 설계만 정리하고 실제 변경은 다음 단계로 넘긴다.

---

## 9. Priority Order

1. reliability
2. UX/UI polish
3. localization/content tone
4. query/ranking validation
5. ops/data hygiene

---

## 10. Selection Logic

- carry-over items 중 query tuning 은 아직 relevant 하지만, 오늘 QA 에서 더 강하게 드러난 문제는 public interaction 과 UI 체감이다.
- 그래서 query tuning 은 오늘 재개하지 않고, 먼저 사용자가 바로 보는 화면의 마찰을 줄이는 쪽으로 우선순위를 바꾼다.
- AI tone / SEO / retention / admin follow-up 은 모두 중요하지만, 지금 당장 한 번에 묶으면 범위가 커져서 안전하지 않다.
- 오늘 QA 는 메인 화면과 공개 페이지 품질에 집중되어 있으므로, 가장 작은 안전한 단위로는 reliability + 같은 화면의 가벼운 UI polish 이 적절하다.

---

## 11. Selected Work for Today

- bucket name: reliability
  - goal: 대표 클릭 경로 1개에서 전체 페이지 새로고침 체감을 줄일 수 있는지 확인하고, 가능하면 부분 업데이트 경로로 축소한다.
  - why selected: 사용자 체감이 가장 크고, 성공하면 제품 인상이 즉시 개선된다.
  - why not deferred: 다음 세션으로 미루면 매번 같은 불편이 반복된다.

- bucket name: UX/UI polish
  - goal: 같은 화면 단위에서 뉴스 테이블 padding 과 source 표기 품질을 최소 수정으로 정리한다.
  - why selected: reliability 작업과 같은 표면을 공유할 가능성이 높아 추가 비용이 낮다.
  - why not deferred: 메인 화면 품질은 지금 바로 보여지는 문제이기 때문이다.

---

## 12. Step Breakdown

### Step 1. Trace the public interaction path

**Goal**
- 메인 화면의 대표 클릭 경로와 테이블 렌더 경로를 추적해서, 전체 새로고침이 어디서 발생하는지 확인한다.

**Target Area**
- controller
- service
- template

**Likely Files**
- `src/main/java/**/controller/**`
- `src/main/java/**/service/**`
- `src/main/resources/templates/**`

**Forbidden Scope**
- 새 아키텍처 도입
- 광범위한 refactor
- query tuning 과 무관한 기능 확장

**Validation**
- 실행 경로를 문서화하고, 수정 후보가 한 화면에 국한되는지 확인한다.

**Expected Output**
- report / analysis note

### Step 2. Apply the smallest safe UI and interaction fix

**Goal**
- 확인된 경로 안에서만 부분 업데이트 또는 UI polish 를 적용해, 메인 화면의 체감 품질을 개선한다.

**Target Area**
- controller
- template
- static assets if needed

**Likely Files**
- `src/main/resources/templates/**`
- `src/main/resources/static/**`
- `src/test/**` if regression coverage already exists

**Forbidden Scope**
- archive / admin / AI summary 전체 재설계
- default language 정책을 이 단계에 혼합
- 데이터 스키마 변경

**Validation**
- 화면 수준에서 동작을 확인하고, 최소한 하나의 재귀 영향 범위만 검증한다.

**Expected Output**
- code
- small verification note

---

## 13. Recommended Agent Flow

1. navi
2. reviewer
3. worker
4. reviewer
5. dockeeper
6. gitter

This order stays unchanged because the selected work is narrow and needs execution-path tracing before any edit.

---

## 14. Codex Execution Notes

- Codex must read:
  - `PROJECT_BRIEF.md`
  - `AGENTS.md`
  - `HARNESS_RULES.md`
  - `DEV_LOOP.md`
  - this strategy file

- Codex must use:
  - `docs/ops/2026-04-06/` only

- Codex must not:
  - create docs outside the date folder
  - modify unrelated files
  - mix multiple concerns in one step

- Codex must:
  - trace first
  - edit only after the path is understood
  - validate before handing off

---

## 15. Risks and Constraints

- scope creep risk: UI polish can easily expand into broad redesign if not constrained.
- regression risk: partial update work can break navigation or state handling if done too widely.
- language risk: default Korean requirement should not be mixed accidentally into the UI polish step.
- documentation risk: if later code inspection contradicts this plan, the code wins and the mismatch must be reported.

---

## 16. Deferrals

- Query noise tuning
  - reason: still relevant but not the highest user-visible issue today.
  - revisit: after the public interaction path is stabilized.

- AI Summary Korean Tone Cleanup
  - reason: belongs to content/locale work, not the smallest safe fix for today.
  - revisit: after the homepage interaction path is verified.

- SEO Foundation Minimal Pass
  - reason: useful, but broader than today's narrow execution scope.
  - revisit: in the next planning pass after UI reliability work.

- Retention Policy Decision
  - reason: needs product-level decision, not just a technical fix.
  - revisit: when archive/retention work is selected as a dedicated step.

- Admin Usage Follow-up
  - reason: operational and data-parity work should not be mixed into this UI step.
  - revisit: when the team is ready to attack admin reliability explicitly.

---

## 17. Definition of Done for Today

- selected step(s) are completed within the narrow scope above
- no unrelated files were touched
- carry-over items were explicitly evaluated
- the main user-facing friction is reduced or at least fully traced
- a clean handoff can be written without guessing

---

## 18. Handoff Requirement

At end of work, MUST generate:

`docs/ops/2026-04-06/DAILY_HANDOFF.md`

It must include:
- completed work
- partial work
- carry-over candidates
- risks
- harness improvements
- next recommended steps
