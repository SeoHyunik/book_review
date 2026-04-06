# TODAY_STRATEGY

## 1. Date

2026-04-06

---

## 2. Strategy Objective

오늘은 사용자가 바로 체감하는 공용 화면의 거친 부분을 최소 범위로 정리한다.
우선 진입 언어, 메인 뉴스 테이블 가독성, 출처 표기, 버튼/헤더의 기본 품질을 안정적으로 다루고, 전체 재설계나 기능 확장은 하지 않는다.

---

## 3. Current Context Summary

- Spring Boot + Thymeleaf 기반의 모놀리식 구조는 유지되고 있다.
- 2026-04-03 세션에서는 provider query noise tuning이 부분 완료 상태로 정리되었다.
- 오늘 QA는 UI/UX 체감 문제와 초기 진입 품질에 집중되어 있다.
- 공용 화면의 작은 개선은 사용자 인상을 크게 바꾸지만, 범위를 잘못 잡으면 디자인 전면 개편으로 커질 수 있다.

---

## 4. Carry-over from Previous Session

- Query noise tuning
  - previous status: partial
  - why it was not completed: provider query 경로만 정리되었고 selector, ranking, UI, AI summary까지 검증되지 않았다.
  - still relevant: yes
  - decision today: defer again

- AI Summary Korean Tone Cleanup
  - previous status: deferred
  - why it was not completed: query tuning과 분리된 별도 작업으로 남아 있었다.
  - still relevant: yes
  - decision today: defer again

- SEO Foundation Minimal Pass
  - previous status: deferred
  - why it was not completed: public route 정리보다 상위의 작업으로 남아 있었다.
  - still relevant: yes
  - decision today: defer again

- Retention Policy Decision
  - previous status: deferred
  - why it was not completed: today-only / archive / delete 정책은 제품 판단이 더 필요하다.
  - still relevant: yes
  - decision today: defer again

- Admin Usage Follow-up
  - previous status: deferred
  - why it was not completed: 운영 화면 개선은 오늘의 공용 화면 QA와 직접 겹치지 않는다.
  - still relevant: yes
  - decision today: defer again

---

## 5. Inputs for Today's Planning

- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-06/QA_INBOX.md`
- `docs/ops/2026-04-06/QA_STRUCTURED.md`
- `docs/ops/2026-04-03/DAILY_HANDOFF.md`
- `docs/reports/` with no new usable report file found

---

## 6. User-Observed Issues

- 초기 진입 언어가 영어로 보인다.
  - where: 첫 진입 화면
  - why it matters: 한국어 우선 제품 인상과 즉시 이해성이 떨어진다.

- 메인페이지 하단 뉴스 테이블이 빽빽하다.
  - where: main page bottom news table
  - why it matters: 제목과 출처를 읽기 어렵고 정보가 눌려 보인다.

- 출처 표기가 너무 거칠다.
  - where: 메인 뉴스 테이블 source column
  - why it matters: `NAVER`만 보이면 실제 언론사 맥락이 사라진다.

- 클릭할 때 전체 페이지가 다시 그려진다.
  - where: public interaction flow
  - why it matters: 사용성 기대치가 낮고 화면이 둔하게 느껴진다.

- 헤더, 버튼, 풋터의 기본 품질이 낮다.
  - where: global layout / common controls
  - why it matters: 사이트 전체의 신뢰감이 약해진다.

- AI 시장 요약 상세페이지가 밋밋하고 한국어 어투가 어색하다.
  - where: AI market summary detail page
  - why it matters: 핵심 콘텐츠의 임팩트와 몰입감이 떨어진다.

---

## 7. Code / System Findings

- 이전 세션의 정리상, provider query 경로는 부분적으로만 안정화되었고 공용 화면 품질과는 별개 축으로 남아 있다.
- 오늘 QA 기준으로는 초기 언어, 테이블 밀도, 출처 표기, 전체 페이지 재로드가 서로 다른 원인처럼 보이므로 한 번에 묶어 해결하면 변경면이 커질 위험이 있다.
- AI 요약 상세페이지의 감성 연출과 한국어 톤은 기능 수정과 분리해서 다루는 편이 안전하다.
- `docs/reports/`에는 오늘 전략을 바로 바꿀 만한 새 분석 보고서가 확인되지 않았다.

---

## 8. Candidate Work Buckets

- reliability
  - why it exists: 초기 진입 품질과 공용 UI의 기본 안정성이 사용자의 첫인상을 좌우한다.
  - scope: 기본 언어, 메인 테이블 가독성, 공용 레이아웃의 최소 안정화

- UX/UI polish
  - why it exists: 헤더, 버튼, 풋터, 상세페이지의 시각적 완성도가 낮다.
  - scope: 기존 구조를 유지하면서 작은 시각 개선만 적용

- localization/content tone
  - why it exists: 한국어 우선 제품인데 일부 문구와 톤이 어색하다.
  - scope: 노출 문구와 기본 언어 우선순위 정리

- query/ranking validation
  - why it exists: 이전 세션 carry-over가 아직 남아 있다.
  - scope: provider query 경로와 결과 품질 검증

---

## 9. Priority Order

1. reliability
2. localization/content tone
3. UX/UI polish
4. query/ranking validation

---

## 10. Selection Logic

- 오늘 QA에서 가장 먼저 드러난 문제는 첫 진입 품질과 메인 공용 화면의 읽기 어려움이다.
- query tuning은 여전히 중요하지만, 오늘은 사용자 체감이 즉시 큰 UI/언어 문제를 먼저 다루는 편이 더 안전하다.
- AI 요약 상세페이지의 감성 연출은 중요하지만, 전면 UI 개편으로 번지기 쉬워 오늘 범위에서는 제외한다.
- SEO, retention, admin usage는 별도 목적과 검증이 필요한 작업이라 오늘 작업과 섞지 않는다.

---

## 11. Selected Work for Today

- bucket name: reliability
  - goal: 초기 진입 언어와 메인 뉴스 테이블의 읽기 어려움을 최소 안전 변경으로 정리한다.
  - why selected: 사용자가 바로 보는 문제이고, 변경면을 작게 잡을 수 있다.
  - why not deferred: 지금 막지 않으면 첫 인상 문제가 계속 누적된다.

- bucket name: localization/content tone
  - goal: 기본 언어 우선순위와 출처 표기 방식의 한국어 자연스러움을 맞춘다.
  - why selected: 한국어 우선 서비스 정체성과 직접 연결된다.
  - why not deferred: 메인 화면과 같은 우선 노출 지점이라 늦추기 어렵다.

---

## 12. Step Breakdown

### Step 1. Trace the public interaction path

**Goal**
- 첫 진입, 메인 뉴스 테이블, 공용 헤더/버튼이 어떤 controller/template 경로로 렌더링되는지 확인한다.

**Target Area**
- controller
- service
- template

**Likely Files**
- `src/main/java/**/controller/**`
- `src/main/java/**/service/**`
- `src/main/resources/templates/**`

**Forbidden Scope**
- query/ranking 로직 변경
- DB schema 변경
- 전체 UI 재설계

**Validation**
- 실제 렌더 경로와 기본 언어 결정 지점을 문서화하고, 수정 지점이 정확히 좁혀졌는지 확인한다.

**Expected Output**
- analysis note

### Step 2. Apply the smallest safe public UI fix

**Goal**
- 기본 언어 우선순위와 메인 뉴스 테이블의 패딩, 출처 표기, 공용 버튼의 기본 품질을 최소 범위로 개선한다.

**Target Area**
- template
- static assets if truly required

**Likely Files**
- `src/main/resources/templates/**`
- `src/main/resources/static/**`

**Forbidden Scope**
- AI summary 로직 변경
- 관리자 기능 확장
- 신규 dependency 추가

**Validation**
- 첫 진입이 한국어 우선으로 보이는지 확인하고, 메인 테이블의 가독성과 출처 식별성이 개선됐는지 점검한다.

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
  - keep the change surface minimal
  - validate before handoff

---

## 15. Risks and Constraints

- scope creep risk: UI polish can easily expand into redesign.
- regression risk: language defaults and shared templates can affect multiple pages.
- consistency risk: Korean phrasing should improve without changing product meaning.
- verification risk: if code inspection later contradicts this plan, the code wins and the mismatch must be reported.

---

## 16. Deferrals

- Query noise tuning
  - reason: still relevant, but not the best fit for today’s narrow public UI pass.
  - revisit: after the public interaction path is traced

- AI Summary Korean Tone Cleanup
  - reason: important, but separate from the safer first UI pass.
  - revisit: when the summary page is selected as the main work item

- SEO Foundation Minimal Pass
  - reason: useful, but broader than today’s bounded fix.
  - revisit: in a dedicated planning step

- Retention Policy Decision
  - reason: needs product-level judgment rather than a small code change.
  - revisit: when archive policy becomes the selected task

- Admin Usage Follow-up
  - reason: operational work should not be mixed into this public UI pass.
  - revisit: when admin reliability is explicitly scheduled

---

## 17. Definition of Done for Today

- selected steps are completed safely
- unrelated files are not modified
- carry-over items are explicitly accounted for
- user-visible issues addressed today are clear
- a clean handoff can be written without ambiguity

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
