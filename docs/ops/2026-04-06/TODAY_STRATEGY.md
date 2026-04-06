# TODAY_STRATEGY

## 1. Date

2026-04-06

---

## 2. Strategy Objective

오늘은 사용자 QA에서 직접 지적된 공용 화면 문제를 좁은 범위로 정리한다. 먼저 공용 진입 경로와 렌더링 흐름을 확인하고, 그 다음 가장 안전한 한두 개의 템플릿/로케일 수정만 수행한다.

---

## 3. Current Context Summary

- Spring Boot + Thymeleaf 모놀리스를 유지한다.
- 2026-04-03 세션에서 provider query noise tuning은 부분 완료 상태로 남아 있다.
- 오늘 QA는 UI/UX 불만이 많지만, 전부를 한 번에 처리하면 범위가 커진다.
- 현재 우선순위는 공용 화면의 첫 인상과 진입 안정성을 먼저 정리하는 것이다.

---

## 4. Carry-over from Previous Session

- Query noise tuning
  - previous status: partial
  - why it was not completed: provider query 쪽만 정리되고 selector, ranking, UI, AI summary까지 함께 검증되지 않았다.
  - still relevant: yes
  - decision today: defer again

- AI Summary Korean Tone Cleanup
  - previous status: deferred
  - why it was not completed: query tuning과 분리된 후속 작업으로 남아 있었다.
  - still relevant: yes
  - decision today: defer again

- SEO Foundation Minimal Pass
  - previous status: deferred
  - why it was not completed: public route와 메타 정리를 별도 step으로 잡아야 한다.
  - still relevant: yes
  - decision today: defer again

- Retention Policy Decision
  - previous status: deferred
  - why it was not completed: today-only / archive / delete 정책은 제품 판단이 필요하다.
  - still relevant: yes
  - decision today: defer again

- Admin Usage Follow-up
  - previous status: deferred
  - why it was not completed: 사용량 정합성은 UI 개선과 다른 축의 작업이다.
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
- `docs/reports/` with no usable new report files found

---

## 6. User-Observed Issues

- 메인 페이지 하단 뉴스 테이블의 좌측 여백이 부족하다.
  - where: 메인 페이지 하단 뉴스 테이블
  - why it matters: 제목 가독성이 떨어지고 표가 답답해 보인다.

- 출처 표기가 너무 뭉뚱그려져 있다.
  - where: 메인 페이지 하단 뉴스 테이블
  - why it matters: NAVER만 보이면 실제 언론사 출처를 구분할 수 없다.

- 첫 진입 시 한국어가 아니라 영문이 먼저 나온다.
  - where: 초기 진입 상태
  - why it matters: 한국어 우선 사용자 경험과 어긋난다.

- 클릭 시 전체 페이지가 새로고침된다.
  - where: 공용 화면 전반
  - why it matters: 사용자가 부분 갱신을 기대하는 흐름에서 끊김이 발생한다.

- AI 시장 요약 상세페이지가 밋밋하고 한국어 어투가 기계적이다.
  - where: AI market summary detail page
  - why it matters: 핵심 제품 페이지의 신뢰감과 몰입감이 약해진다.

- header, footer, 버튼 모양이 세련되지 않다.
  - where: header / footer / action buttons
  - why it matters: 공용 화면 전체의 품질 인상이 낮아진다.

---

## 7. Code / System Findings

- 공용 페이지 문제는 한 번에 다 고치기보다, 먼저 실제 렌더링 경로를 확인한 뒤 template 단위로 좁히는 편이 안전하다.
- default language, 표 열 여백, 출처 표기, 전체 페이지 새로고침은 서로 다른 원인일 가능성이 높다.
- query tuning carry-over는 여전히 중요하지만, 오늘 QA에서 직접 눈에 띈 UX 문제보다 우선순위는 낮다.
- `docs/reports/`에서 오늘 전략에 직접 연결되는 최신 분석 파일은 확인되지 않았다.

---

## 8. Candidate Work Buckets

- reliability
  - why it exists: 공용 진입 경로와 기본 상태가 흔들리면 사용자가 바로 이탈한다.
  - scope: 초기 언어, 전체 페이지 새로고침, 공용 화면 렌더링 경로 확인.

- UX/UI polish
  - why it exists: 메인 테이블, header/footer, 버튼 품질, AI summary detail이 모두 시각적으로 약하다.
  - scope: 가장 좁은 템플릿 수정만 선택해서 적용.

- localization/content tone
  - why it exists: 한국어 우선 진입과 AI 문구의 자연스러움이 핵심 사용자 경험이다.
  - scope: 기본 언어와 문구 품질을 최소 범위에서 정리.

- query/ranking validation
  - why it exists: 이전 세션의 carry-over 이슈가 아직 남아 있다.
  - scope: provider query 경로와 결과 정합성 확인.

---

## 9. Priority Order

1. reliability
2. UX/UI polish
3. localization/content tone
4. query/ranking validation

---

## 10. Selection Logic

- 오늘 QA는 공용 화면 품질에 집중되어 있어 reliability와 UX/UI polish가 먼저다.
- carry-over인 query tuning은 중요하지만, 오늘은 직접 눈에 보이는 사용자 문제를 먼저 줄이는 쪽이 더 가치가 크다.
- 한국어 톤 문제는 중요하지만, 오늘의 최소 안전 변경 범위에서는 로케일/진입 상태 정도로만 제한한다.
- SEO, retention, admin usage는 각각 의미가 있지만 오늘 step에 섞으면 범위가 커진다.

---

## 11. Selected Work for Today

- bucket name: reliability
  - goal: 공용 진입 경로와 초기 상태를 확인해 전체 페이지 새로고침과 기본 언어 문제의 원인을 좁힌다.
  - why selected: 사용자 체감도가 높고, 이후 UI 수정의 전제 조건이기 때문이다.
  - why not deferred: 지금 확인하지 않으면 다른 UI 수정이 정확한 범위로 들어가지 않는다.

- bucket name: UX/UI polish
  - goal: 확인된 template에서 테이블 여백, 출처 표기, 버튼 품질 중 가장 좁은 한두 개만 수정한다.
  - why selected: 오늘 QA의 가장 직접적인 불만이 시각 품질이기 때문이다.
  - why not deferred: 공용 화면 인상 개선을 너무 늦추면 제품 신뢰감이 계속 떨어진다.

- bucket name: localization/content tone
  - goal: 첫 진입 한국어 기본값과 기계적인 문구 문제를 최소 범위에서 정리한다.
  - why selected: 한국어 우선 사용자에게 바로 드러나는 문제이기 때문이다.
  - why not deferred: UX 수정과 분리하면 다시 후순위로 밀릴 가능성이 높다.

---

## 12. Step Breakdown

### Step 1. Trace the public interaction path

**Goal**
- 메인 페이지, header/footer, archive page, AI summary detail page가 어떤 controller/template 경로로 렌더링되는지 확인한다.

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
- 광범위한 리팩토링

**Validation**
- 실제 요청 경로와 template 바인딩을 확인하고, 수정 대상이 정확히 어디인지 기록한다.

**Expected Output**
- analysis note

### Step 2. Apply the smallest safe UI and locale fix

**Goal**
- 확인된 template 범위 안에서 기본 언어, 테이블 여백, 출처 표기 중 가장 안전한 항목만 최소 수정한다.

**Target Area**
- template
- static assets if truly required

**Likely Files**
- `src/main/resources/templates/**`
- `src/main/resources/static/**`

**Forbidden Scope**
- admin flow 전체 개편
- AI summary 알고리즘 변경
- 새 dependency 추가

**Validation**
- 수정 후 공용 페이지가 깨지지 않는지 확인하고, 변경 범위가 템플릿 안으로 제한되었는지 점검한다.

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

- scope creep risk: UI polish can expand into redesign if not constrained.
- regression risk: partial update work can break navigation or state handling if done too widely.
- language risk: Korean default behavior should not be mixed accidentally with unrelated styling changes.
- documentation risk: if code inspection later contradicts this plan, the code wins and the mismatch must be reported.

---

## 16. Deferrals

- Query noise tuning
  - reason: still relevant, but not the best use of today's narrow step.
  - revisit: after the public interaction path is stabilized.

- AI Summary Korean Tone Cleanup
  - reason: important, but not safe to mix broadly with today’s first pass.
  - revisit: when the summary page is selected as the main step.

- SEO Foundation Minimal Pass
  - reason: useful, but broader than today's bounded UI work.
  - revisit: in a dedicated planning pass.

- Retention Policy Decision
  - reason: needs product-level judgment, not a narrow code fix.
  - revisit: when archive/retention is selected as its own work item.

- Admin Usage Follow-up
  - reason: operational parity work should not be mixed into this UI step.
  - revisit: when admin reliability is explicitly scheduled.

---

## 17. Definition of Done for Today

- selected steps are completed safely
- unrelated files are not modified
- carry-over items are explicitly accounted for
- the user-visible issues addressed today are clear
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
