# TODAY_STRATEGY

## 1. Date

2026-04-06

---

## 2. Strategy Objective

오늘은 공개 화면에서 가장 먼저 보이는 공통 UI와 첫 진입 언어/문구를 작은 안전 변경으로 정리한다.
목표는 `/news` 중심의 공개 경험을 덜 거칠게 만들고, 사용자가 즉시 체감하는 품질 문제를 좁은 범위에서 줄이는 것이다.

---

## 3. Current Context Summary

- Spring Boot + Thymeleaf 모놀리스 구조는 유지되고 있다.
- 2026-04-06 보고서에서 `/` → `/news`, `NewsController.list()`, `templates/news/list.html`, `fragments/layout.html`, `GlobalUiModelAttributes` 경로가 확인되었다.
- 공개 진입 경로는 이미 추적되어 있어, 오늘은 재탐색보다 최소 수정에 집중할 수 있다.
- QA는 공통 헤더/푸터, 메인 뉴스 테이블, 버튼, 첫 진입 언어, 부분 새로고침 체감, AI 시장 요약 상세의 톤 문제를 공통적으로 지적한다.

---

## 4. Carry-over from Previous Session

- Query noise tuning
  - previous status: partial
  - why it was not completed: provider query 경로만 정리되고, selector/ranking/UI/summary 영향은 별도 검증이 남아 있었다.
  - still relevant: no
  - decision today: drop
  - note: 2026-04-06 보고서가 공개 상호작용 경로 추적을 완료했으므로 오늘의 주력 계획에서 제외한다.

- AI Summary Korean Tone Cleanup
  - previous status: deferred
  - why it was not completed: query tuning과 분리된 별도 작업으로 남아 있었다.
  - still relevant: yes
  - decision today: continue now
  - note: inbox와 structured QA 모두 AI 시장 요약 상세 페이지의 기계적인 한국어 톤을 지적한다.

- SEO Foundation Minimal Pass
  - previous status: deferred
  - why it was not completed: public route 안정화보다 우선도가 낮았다.
  - still relevant: yes
  - decision today: defer again

- Retention Policy Decision
  - previous status: deferred
  - why it was not completed: today-only / archive / delete 정책은 제품 판단이 필요하다.
  - still relevant: yes
  - decision today: defer again

- Admin Usage Follow-up
  - previous status: deferred
  - why it was not completed: 공개 화면 안정화와 분리된 운영성 작업이다.
  - still relevant: yes
  - decision today: defer again

- Partial Update Pilot
  - previous status: deferred
  - why it was not completed: 템플릿 경계와 공통 레이아웃 영향 범위를 먼저 확인해야 했다.
  - still relevant: yes
  - decision today: continue now
  - note: inbox의 "클릭할 때 전체 페이지가 새로고침되는 현상"이 오늘 QA의 핵심 증상이다.

---

## 5. Inputs for Today's Planning

- `PROJECT_BRIEF.md`
- `AGENTS.md`
- `DEV_LOOP.md`
- `HARNESS_RULES.md`
- `docs/ops/TODAY_STRATEGY_FORMAT.md`
- `docs/ops/2026-04-06/QA_STRUCTURED.md`
- `docs/ops/2026-04-06/QA_INBOX.md`
- `docs/ops/2026-04-03/DAILY_HANDOFF.md`
- `docs/reports/2026-04-06-step-1-public-interaction-path.md`

---

## 6. User-Observed Issues

- 첫 진입 언어가 영어로 보인다.
  - where: 초기 진입 / public landing flow
  - why it matters: 한국어 서비스 인상과 즉시 이해도가 떨어진다.

- 메인 뉴스 테이블이 답답하고 출처 표기가 뭉개진다.
  - where: 메인페이지 하단 뉴스 테이블
  - why it matters: 제목과 출처를 빠르게 읽기 어렵고, 신뢰감이 떨어진다.

- 클릭할 때 전체 페이지가 다시 그려지는 체감이 있다.
  - where: 공통 상호작용 경로
  - why it matters: React/Vue 같은 부분 갱신 기대와 대비되어 UX가 낡아 보인다.

- 공통 헤더, 버튼, 푸터가 세련되지 않다.
  - where: global layout controls
  - why it matters: 사이트 전반의 첫인상을 약하게 만든다.

- AI 시장 요약 상세 페이지가 밋밋하고 한국어 어투가 기계적이다.
  - where: AI market summary detail page
  - why it matters: 핵심 콘텐츠의 감정적 임팩트와 읽는 맛이 약하다.

- 아카이브 페이지는 페이징과 중복 제목 정리가 부족하다.
  - where: archive page
  - why it matters: 탐색성과 검색성이 떨어진다.

---

## 7. Code / System Findings

- `/` 는 `PageController`에서 `/news`로 리다이렉트되고, 공개 목록은 `NewsController.list()`가 `news/list` 템플릿을 렌더링한다.
- 상세 진입은 같은 컨트롤러의 `@GetMapping("/{id}")` 경로와 `AnonymousDetailViewGateService`를 함께 탄다.
- 공통 헤더/언어/로그인/관리자 버튼은 `templates/fragments/layout.html`과 `GlobalUiModelAttributes`에 집중되어 있어, 이 영역은 블라스트 반경이 크다.
- 메인 뉴스 테이블의 열 폭, 패딩, 출처 표기, 버튼 질감은 `templates/news/list.html` 쪽에서 직접 확인하고 고쳐야 한다.
- 2026-04-06 보고서는 공개 상호작용 경로를 이미 추적했기 때문에, 오늘은 추가 탐색보다 검증 가능한 국소 수정이 더 안전하다.
- QA_STRUCTURED는 공통 UI shell 정리와 언어/출처/부분 갱신 문제를 하나의 안정화 묶음으로 본다.
- QA_INBOX는 그 안에 아카이브 페이징, AI 요약 카드 임팩트, 로그인 해제, admin/collection 이슈까지 더 넓은 요구를 섞어 두었다.

---

## 8. Candidate Work Buckets

- reliability
  - why it exists: 첫 진입 언어, 공통 레이아웃, 뉴스 테이블, 부분 새로고침 체감은 사용자가 가장 먼저 만나는 신뢰성 문제다.
  - scope: 기본 언어, 출처 표기, 클릭 시 전체 새로고침 체감 완화, 공개 진입 안정성

- UX/UI polish
  - why it exists: header, footer, button, table spacing, archive surface의 품질이 사이트 인상을 결정한다.
  - scope: 공통 쉘, 버튼 테두리, 테이블 패딩, 아카이브의 기본 시각 정리

- localization/content tone
  - why it exists: AI 시장 요약과 공통 문구는 한국어 서비스의 정체성을 직접 만든다.
  - scope: 기계적인 한국어 완화, 자연스러운 안내 문구, 공개 표기 정리

- broader product decisions
  - why it exists: retention, admin usage, SEO, 데이터 지표, 카드 캐러셀, 접근제어는 별도 판단이 필요하다.
  - scope: 오늘은 확장하지 않는다

---

## 9. Priority Order

1. reliability
2. UX/UI polish
3. localization/content tone
4. broader product decisions

---

## 10. Selection Logic

- 오늘 QA는 "가장 먼저 보이는 공개 화면"의 품질 문제를 반복해서 지적했고, structured QA도 같은 축으로 묶었다.
- 따라서 공통 레이아웃과 메인 뉴스 테이블, 첫 진입 언어, 부분 새로고침 체감처럼 사용자 체감이 큰 부분을 먼저 선택한다.
- AI 요약 상세의 톤 문제는 분리된 제품 기능처럼 보이지만, 실제로는 public-facing copy 품질이므로 오늘의 localization 범위에 포함한다.
- 반면 retention, admin usage, SEO, 데이터 지표, 카드 캐러셀은 제품 판단 또는 더 넓은 변경을 요구하므로 오늘은 보류한다.
- QA_INBOX는 구조적으로 더 세분화되어 있지만, structured QA와 충돌하지는 않는다. 단지 범위를 더 넓게 펼쳐 놓았을 뿐이므로, 오늘은 그중 안전한 부분만 가져간다.

---

## 11. Selected Work for Today

- bucket name: reliability
  - goal: 공개 진입 언어, 출처 표기, 부분 새로고침 체감, 기본 표시 상태를 안정화한다.
  - why selected: 사용자 체감이 가장 크고, 국소 수정으로 검증 가능하다.
  - why not deferred: 오늘의 QA 핵심이다.

- bucket name: UX/UI polish
  - goal: header, footer, 버튼, 뉴스 테이블, 아카이브의 기본 질감을 정리한다.
  - why selected: 공통 레이아웃에서 발생하는 낡은 인상을 줄일 수 있다.
  - why not deferred: 공통 쉘은 한 번 손보면 여러 화면에 동시에 반영된다.

- bucket name: localization/content tone
  - goal: AI 시장 요약과 공개 문구의 기계적인 한국어를 줄인다.
  - why selected: 기능 변경 없이도 체감 품질을 올릴 수 있다.
  - why not deferred: 오늘 QA가 직접 지적한 문제다.

---

## 12. Step Breakdown

### Step 1. Trace the public interaction path

**Goal**
- `/` → `/news` → 상세 진입 → 공통 레이아웃으로 이어지는 실제 렌더링 경로를 다시 확인하고, 오늘 손댈 수 있는 파일만 좁힌다.

**Target Area**
- controller
- service
- template

**Likely Files**
- `src/main/java/**/controller/**`
- `src/main/java/**/service/**`
- `src/main/resources/templates/fragments/layout.html`
- `src/main/resources/templates/news/list.html`
- `src/main/resources/templates/news/detail.html`
- `src/main/java/**/controller/GlobalUiModelAttributes.java`

**Forbidden Scope**
- query/ranking 로직 변경
- DB schema 변경
- 관리자 기능 변경
- SEO 전체 재설계

**Validation**
- report와 실제 코드 경로가 일치하는지 확인하고, 수정 대상 파일만 적는다.

**Expected Output**
- analysis note

### Step 2. Apply the smallest safe public-facing fix set

**Goal**
- 첫 진입 언어, 뉴스 테이블 여백/출처 표기, 버튼 질감, 공통 문구 톤처럼 사용자가 바로 느끼는 부분만 최소 변경한다.

**Target Area**
- template
- static assets
- controller model attributes if strictly needed

**Likely Files**
- `src/main/resources/templates/fragments/layout.html`
- `src/main/resources/templates/news/list.html`
- `src/main/resources/templates/news/detail.html`
- `src/main/resources/static/**`
- `src/main/java/**/controller/GlobalUiModelAttributes.java`

**Forbidden Scope**
- AI 해석 알고리즘 변경
- 뉴스 수집 파이프라인 변경
- 접근제어 정책 변경
- 대규모 UI 재설계

**Validation**
- 공통 쉘과 뉴스 목록에서 언어, 출처, 패딩, 버튼이 더 자연스럽게 보이는지 확인한다.
- 클릭 동작이 전체 새로고침처럼 느껴지는 부분은 구조를 바꾸지 않는 선에서만 완화한다.

**Expected Output**
- code
- small verification note

### Step 3. Re-review and handoff

**Goal**
- 이번 변경이 공개 화면에만 국소적으로 머무는지 재검토하고, 남은 넓은 범위 작업을 명확히 분리한다.

**Target Area**
- review
- docs

**Likely Files**
- `docs/ops/2026-04-06/DAILY_HANDOFF.md`

**Forbidden Scope**
- 추가 기능 확장
- 새 토픽 착수

**Validation**
- selected work와 deferred work가 문서에 분리되어 있는지 확인한다.

**Expected Output**
- review note
- daily handoff input

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
  - mix broad product work into today's UI pass

- Codex must:
  - trace first
  - keep the change surface minimal
  - validate before handoff

---

## 15. Risks and Constraints

- scope creep risk: UI polish can easily turn into a redesign if boundaries are not enforced.
- blast radius risk: `layout.html` and `GlobalUiModelAttributes` affect many pages at once.
- consistency risk: Korean wording must improve without changing product meaning.
- interaction risk: partial update work can spill into controller/template contracts if pushed too far.
- mismatch risk: QA_INBOX contains broader product wishes that are not selected today; they must stay separated.

---

## 16. Deferrals

- SEO Foundation Minimal Pass
  - reason: useful, but wider than today's bounded public UI pass.
  - when to revisit: after the public shell and language pass stabilizes

- Retention Policy Decision
  - reason: requires product-level judgment, not just code movement.
  - when to revisit: when archive policy is explicitly scheduled

- Admin Usage Follow-up
  - reason: operational cleanup should not be mixed with public UI stabilization.
  - when to revisit: when admin work is the selected work item

- Query noise tuning
  - reason: the 2026-04-06 report already traced the public route path; today's concern shifted elsewhere.
  - when to revisit: only if later QA again points to collection relevance or ranking noise

- Broad AI market summary redesign
  - reason: the user wants stronger impact, but that is a broader product/design pass.
  - when to revisit: after today's bounded copy and shell fixes

---

## 17. Definition of Done for Today

- selected steps are completed safely
- unrelated files are not modified
- carry-over items are explicitly accounted for
- public-facing UI and language issues are reduced without broad redesign
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
