# TODAY_STRATEGY

## 1. Date

2026-04-06

---

## 2. Strategy Objective

오늘은 공용 화면에서 사용자가 바로 느끼는 불편을 좁은 범위로 정리한다. 먼저 메인/아카이브/헤더의 실제 렌더링 경로를 추적하고, 그 결과를 바탕으로 가장 작은 UI 및 언어 정리만 진행한다. 전면 개편이나 기능 확장은 하지 않는다.

---

## 3. Current Context Summary

- Spring Boot + Thymeleaf 모놀리스 방향은 유지되고 있다.
- 2026-04-03 세션에서 provider query noise tuning은 부분 완료 상태로 남아 있다.
- 오늘 QA에서는 공용 화면이 딱딱하고, 최초 진입 언어가 영어로 먼저 나오며, 클릭할 때 전체 페이지가 새로고침되는 체감이 강하게 보고되었다.
- 메인 하단 뉴스 테이블, 버튼, header/footer, archive page, AI 시장 요약 상세페이지가 모두 UX 불만 지점으로 올라왔다.
- `docs/reports/`에는 오늘 바로 재사용할 만한 최신 보고서가 보이지 않는다.

---

## 4. Carry-over from Previous Session

- Query noise tuning
  - previous status: partial
  - why it was not completed: provider query 범위만 좁힌 상태이고 selector / ranking / UI / AI summary 쪽은 아직 검증이 덜 됐다.
  - still relevant: yes
  - decision today: defer again

- AI Summary Korean Tone Cleanup
  - previous status: deferred
  - why it was not completed: query tuning과 분리된 별도 작업으로 남겨 두었다.
  - still relevant: yes
  - decision today: defer again

- SEO Foundation Minimal Pass
  - previous status: deferred
  - why it was not completed: public route 메타와 콘텐츠 구조 보강이 별도 단계로 필요하다.
  - still relevant: yes
  - decision today: defer again

- Retention Policy Decision
  - previous status: deferred
  - why it was not completed: today-only / archive / delete 정책은 제품 결정이 필요하다.
  - still relevant: yes
  - decision today: defer again

- Admin Usage Follow-up
  - previous status: deferred
  - why it was not completed: 사용량 / 비용 정합성은 UI 정리와 다른 축이다.
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
- `docs/reports/`
- 오늘자 사용자 QA 메모

---

## 6. User-Observed Issues

- 메인 페이지 하단 뉴스 테이블의 좌측 패딩이 부족하고 출처 표기가 거칠다.
  - where: 메인 페이지 하단 뉴스 테이블
  - why it matters: 가독성이 낮고 정보 신뢰도가 떨어진다.

- 화면의 일부를 눌러도 전체 페이지가 새로고침된다.
  - where: 공용 UI 전반
  - why it matters: 사용자는 부분 갱신형 인터랙션을 기대하고 있으며, 현재 경험은 낡아 보인다.

- 최초 진입 시 기본 언어가 영어로 먼저 나온다.
  - where: 초기 진입 상태
  - why it matters: 한국어 서비스 정체성과 바로 충돌한다.

- AI 시장 요약 상세페이지가 밋밋하고 문장이 기계적으로 느껴진다.
  - where: AI market summary detail page
  - why it matters: 핵심 상품의 체감 품질을 낮춘다.

- archive page와 header/footer, 버튼 스타일이 전반적으로 세련되지 않다.
  - where: archive page, header, footer, action buttons
  - why it matters: 첫인상과 반복 방문 품질을 동시에 떨어뜨린다.

- 관리자 자동 수집 흐름이 멈춘 것으로 보인다.
  - where: admin auto-collection flow
  - why it matters: 콘텐츠 공급이 멈추면 공용 화면 개선 효과도 제한된다.

---

## 7. Code / System Findings

- 이전 세션의 결과는 provider query 범위를 좁히는 bounded change로 정리됐고, selector / ranking / UI / AI summary와 섞지 말아야 한다는 경계가 남아 있다.
- 오늘 QA는 최소 3개의 분리된 축을 드러냈다. public rendering, locale/default state, content generation tone은 같은 문제가 아니라 별도 축이다.
- Spring Boot + Thymeleaf 모놀리스 구조상, 오늘은 먼저 실제 렌더링 경로를 확인한 뒤 template 단위의 작은 수정만 하는 편이 안전하다.
- `docs/reports/`에 바로 참조할 최신 보고서가 없어, 오늘 계획은 QA와 handoff를 1차 근거로 삼는 편이 맞다.

---

## 8. Candidate Work Buckets

- reliability
  - why it exists: 공용 화면에서 “클릭하면 전체가 새로고침된다”는 체감은 사용자 신뢰를 직접 깎는다.
  - scope: 메인/헤더/아카이브의 실제 렌더링 경로를 추적하고, 부분 갱신 가능 여부를 확인한다.

- UX/UI polish
  - why it exists: 메인 테이블, 버튼, header/footer, archive page가 모두 밋밋하고 거칠다.
  - scope: 패딩, 출처 라벨, 버튼 모양, 기본 레이아웃 톤을 최소 범위에서 정리한다.

- localization/content tone
  - why it exists: 최초 진입 언어와 AI 요약 문체가 한국어 서비스 기대치와 맞지 않는다.
  - scope: 기본 언어 및 대표 문구의 한국어 우선 정렬 여부를 확인한다.

- query/ranking validation
  - why it exists: query tuning은 아직 carry-over 상태다.
  - scope: provider query와 결과 정합성을 별도 검증 대상으로 유지한다.

- ops/data hygiene
  - why it exists: 관리자 수집과 보관 정책은 공용 UX와 독립된 운영 과제다.
  - scope: 수집 정지, retention, usage parity는 별도 단계에서 다룬다.

---

## 9. Priority Order

1. reliability
2. UX/UI polish
3. localization/content tone
4. query/ranking validation
5. ops/data hygiene

---

## 10. Selection Logic

- carry-over 중 query tuning은 여전히 중요하지만, 오늘 QA의 직접 불편은 공용 화면 체감 품질이다.
- 그래서 오늘은 검색/랭킹 쪽을 다시 열지 않고, 사용자가 바로 보는 메인 경로의 신뢰성과 최소 UI 정리로 범위를 좁힌다.
- AI tone, SEO, retention, admin follow-up은 모두 중요하지만 오늘 한 번에 묶으면 범위가 커져서 안전성이 떨어진다.
- trade-off는 명확하다. 오늘은 넓게 고치지 않고, 대신 공용 화면의 핵심 경로를 좁고 안전하게 정리한다.

---

## 11. Selected Work for Today

- bucket name: reliability
  - goal: 메인/헤더/아카이브 중 실제로 가장 많이 보이는 공용 경로에서 전체 새로고침 체감이 왜 생기는지 확인하고, 가능한 경우 부분 갱신 지점만 좁혀 본다.
  - why selected: 사용자 체감이 가장 크고, 다른 UI 개선보다 우선해 원인 경로를 알아야 한다.
  - why not deferred: 지금 미루면 오늘 QA의 핵심 불만을 계속 방치하게 된다.

- bucket name: UX/UI polish
  - goal: 확인된 범위 안에서만 메인 테이블 패딩, 출처 표기, 버튼 시각 품질, 기본 한국어 진입 상태를 최소 변경으로 다듬는다.
  - why selected: 신뢰성 경로와 같은 템플릿에 붙어 있을 가능성이 높아, 함께 확인하는 편이 효율적이다.
  - why not deferred: 공용 화면의 첫인상은 오늘 바로 개선 가능한 낮은 위험의 영역이다.

---

## 12. Step Breakdown

### Step 1. Trace the public interaction path

**Goal**
- 메인 페이지, archive page, header/footer가 어떤 controller/template 경로로 렌더링되는지 확인하고, 전체 새로고침 체감의 실제 원인을 좁힌다.

**Target Area**
- controller
- service
- template

**Likely Files**
- `src/main/java/**/controller/**`
- `src/main/java/**/service/**`
- `src/main/resources/templates/**`

**Forbidden Scope**
- query/ranking 로직 재설계
- 전면 리팩터링
- DB schema 변경

**Validation**
- 실제 호출 경로와 템플릿 연결을 문서화하고, 수정 대상이 한두 개 템플릿으로 제한되는지 확인한다.

**Expected Output**
- analysis note

### Step 2. Apply the smallest safe UI and locale fix

**Goal**
- 확인된 템플릿 범위 안에서만 패딩, 출처 표기, 버튼 시각 품질, 기본 한국어 진입 상태를 최소 변경으로 정리한다.

**Target Area**
- template
- static assets if needed

**Likely Files**
- `src/main/resources/templates/**`
- `src/main/resources/static/**`

**Forbidden Scope**
- archive / admin / AI summary 전체 재설계
- 새로운 dependency 추가
- 기능 확장

**Validation**
- 화면이 깨지지 않는지 확인하고, 수정 전보다 가독성과 초기 진입 언어가 명확히 개선됐는지 점검한다.

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

이 순서를 유지한다. 오늘 범위는 좁지만, 실제 렌더링 경로를 먼저 확인해야 안전하게 수정할 수 있다.

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

- 선택한 좁은 범위의 단계가 완료된다.
- unrelated files는 건드리지 않는다.
- carry-over 항목을 명시적으로 평가한다.
- 사용자가 바로 보는 불편이 줄거나, 최소한 원인이 명확히 추적된다.
- 다음 세션이 추측 없이 이어질 수 있는 handoff를 만들 수 있다.

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
