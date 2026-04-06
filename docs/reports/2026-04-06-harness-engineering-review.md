# 2026-04-06 하니스 엔지니어링 점검 보고서

## 범위
- 확인 범위는 현재 브랜치의 하니스 관련 변경 이력 전체입니다.
- Git 로그상 가장 이른 하니스 전용 커밋은 `31b0230`이며, 작성 시각은 `2026-04-06 10:26`입니다.
- 따라서 실제 점검 기준은 `31b0230..HEAD`입니다.
- 점검 대상은 `tools/run-harness.ps1`, `.codex/prompts/2026-04-06/*`, `docs/ops/*`, `docs/reports/*`, 그리고 같은 작업 흐름에서 함께 수정된 템플릿 파일들입니다.

## 결론
- 이번 구간의 핵심 변화는 하니스 실행기가 단순 프롬프트 실행기에서 역할 기반 오케스트레이터로 바뀐 점입니다.
- 가장 큰 실질 변경은 `tools/run-harness.ps1` 하나에 집중되어 있습니다.
- 그 외 변경은 대부분 날짜별 프롬프트 생성, ops 문서 생성, handoff 보강, 그리고 public UI 정리입니다.
- 제품 코드 자체의 구조 변경은 거의 없고, 대부분은 Thymeleaf 템플릿의 표시 품질 개선입니다.

## 변경 흐름 요약

### 1. 하니스 러너가 역할 기반 워크플로우로 확장됨
- `tools/run-harness.ps1`가 `planner`, `qa-structurer`, `step`, `curator`, `handoff`, `all`에 더해 `workday` 모드를 갖게 됐습니다.
- `PauseForQa`가 추가되어 단계 사이에 QA 확인 지점을 둘 수 있게 됐습니다.
- 실행 중 어떤 stage가 실제로 돌았는지 추적하는 요약 출력이 추가됐습니다.
- 프롬프트는 `.codex/prompts/<date>/` 아래에 날짜별로 저장되도록 바뀌었습니다.
- `gitter` 역할이 분리되어 step 실행 후 git diff 검사와 커밋 단계를 별도로 다룹니다.

### 2. QA와 handoff 사이의 게이트가 강화됨
- `QA_INBOX.md`에 실제 액션 아이템이 있으면 `QA_STRUCTURED.md`가 먼저 채워져 있어야 planner가 실행되도록 막습니다.
- handoff 전에 `QA_INBOX.md`, `QA_STRUCTURED.md`, `TODAY_STRATEGY.md`가 모두 읽을 수 있는 UTF-8인지 검증합니다.
- `Test-TextLooksCorrupted()`로 깨진 인코딩 패턴을 탐지하려고 합니다.
- step 실행 후에는 새로운 비-ops 변경이 실제로 생겼는지 검증합니다.
- analysis step으로 분류되면 `docs/reports/` 산출물이 없을 때 실패하도록 했습니다.

### 3. 다음 날 작업 초안까지 자동으로 준비함
- `Initialize-NextDayCarryOverDraft()`가 추가되어, 구조화된 QA가 있으면 다음 날짜 폴더를 미리 만들고 carry-over 초안을 생성합니다.
- 이 동작은 handoff 품질을 높이지만, 다음 날의 작업 상태를 미리 만들어 두는 부작용도 있습니다.

### 4. 운영 문서와 프롬프트가 날짜 단위로 재구성됨
- `.codex/prompts/2026-04-06/` 아래에 planner, qa-structurer, step1~3, gitter-step1~3, curator, handoff 프롬프트가 분리되어 있습니다.
- root 수준에 흩어져 있던 prompt 파일은 날짜 디렉터리 구조로 이동했습니다.
- `docs/ops/HARNESS_FAILURES.md`는 반복 실패를 누적 기록하는 장소로 계속 확장됐습니다.

### 5. 제품 UI도 같은 작업 흐름에서 함께 손봐졌음
- `src/main/resources/templates/fragments/layout.html`의 `lang`이 `en`에서 `ko`로 바뀌었습니다.
- `src/main/resources/templates/news/list.html`은 소스 라벨 표시, 테이블 spacing, 배지 스타일, 줄바꿈 규칙이 조정됐습니다.
- 이 변경은 하니스 자체가 아니라 제품 렌더링 품질 개선입니다.

## 파일별 점검 결과

### `tools/run-harness.ps1`
- 가장 큰 변경 파일입니다.
- 현재 구조상 하니스는 단순히 프롬프트를 실행하는 스크립트가 아니라, QA 정규화, 전략 생성, step 실행, 커밋 전 점검, handoff, 다음날 초안 생성까지 묶는 실행 엔진에 가깝습니다.
- 관련 핵심 지점은 `Get-PlannedStepNumbers()`(라인 300), `Test-TextLooksCorrupted()`(라인 338), `Assert-QaStructuredReadyForPlanning()`(라인 405), `Assert-PreHandoffReadiness()`(라인 424), `Invoke-QaStructurerMode()`(라인 753), `Invoke-PlannerMode()`(라인 761), `Invoke-GitterMode()`(라인 771), `Initialize-NextDayCarryOverDraft()`(라인 948), `Invoke-WorkdayMode()`(라인 1003)입니다.
- 좋았던 점은, ops 문서의 완결성과 UTF-8 가독성을 하니스 차원에서 체크하기 시작한 점입니다.
- 남은 위험은 인코딩 감지와 step 분류가 정규식 기반 휴리스틱이라서, 실제 오류를 놓치거나 잘못 분류할 가능성이 있다는 점입니다.

### `.codex/prompts/2026-04-06/*`
- role별 프롬프트가 분리되면서 실행 컨텍스트가 명확해졌습니다.
- `planner`는 `QA_STRUCTURED.md`를 주 입력으로 쓰고 `QA_INBOX.md`는 교차 검증용으로만 보게 됩니다.
- `step` 프롬프트는 분석형 step과 구현형 step을 구분하는 의도를 포함합니다.
- `curator`와 `handoff`는 읽기 전용 컨텍스트를 분리해서 하니스 실패 기록과 일일 handoff를 다룹니다.

### `docs/ops/2026-04-06/*`
- `TODAY_STRATEGY.md`, `QA_STRUCTURED.md`, `DAILY_HANDOFF.md`가 모두 날짜별 컨텍스트로 유지됩니다.
- 다만 현재 저장소 상태에서는 `TODAY_STRATEGY.md`에 여전히 깨진 한글이 남아 있습니다.
- `HARNESS_FAILURES.md`에는 “handoff 누락”, “QA 구조화 미적용”, “문서 인코딩 훼손”, “stale ops context”가 반복 실패로 기록돼 있습니다.
- 즉, 하니스는 실패를 감지하도록 강화됐지만, 산출물 자체는 아직 완전히 정리되지 않았습니다.

### `src/main/resources/templates/news/list.html`
- `lang="ko"`로 바뀌었고, news source 표시와 테이블 spacing이 조정됐습니다.
- featured 영역과 list 영역에서 source 라벨을 더 읽기 쉽게 노출하도록 바뀌었습니다.
- 하니스 관점에서는 이 파일이 실행 엔진의 일부는 아니지만, 같은 작업 세션에서 public-facing UX를 손본 결과물입니다.

### `src/main/resources/templates/fragments/layout.html`
- `lang="ko"` 변경만 있는 비교적 작은 수정입니다.
- 전체 공용 레이아웃의 언어 선언이 정리된 점은 SEO와 접근성에 의미가 있습니다.

## 커밋 흐름 해석
- `31b0230`에서 하니스 기본 scaffold가 시작됐습니다.
- `24de65d`부터 `workday`와 step/curator/handoff 프롬프트 체계가 확장됐습니다.
- `eae1da5`~`7dd79cf` 구간에서는 `tools/run-harness.ps1`가 계속 강화되며, QA 구조화와 handoff 검증이 붙었습니다.
- `c541b71`와 `f372372`에서는 하니스 문서와 public UI 수정이 함께 섞였습니다.
- `dc66c94`와 `410f2aa`는 리뷰 handoff와 다음날 ops 초안 생성까지 포함해 일일 루프를 완성하는 방향으로 마무리됐습니다.

## 남은 리스크
- 인코딩 검사와 step 목적 판별이 휴리스틱에 의존합니다.
- `workday` 모드는 편리하지만, 실패 시 원인 범위가 넓어질 수 있습니다.
- 다음날 초안 자동 생성은 운영을 돕지만, 실제로는 아직 검토되지 않은 상태의 파일을 미리 만들어 둘 수 있습니다.
- 현재 문서 상태상 `HARNESS_FAILURES.md`가 지적하는 일부 문제는 완전히 사라지지 않았습니다.
- 제품 UI 수정이 하니스 작업과 같은 커밋 스트림에 섞여 있어, 나중에 원인 추적 시 범위 구분이 필요합니다.

## 최종 판단
- 하니스 엔지니어링은 이번 구간에서 분명히 성숙했습니다.
- 특히 `tools/run-harness.ps1`는 “프롬프트 실행기”에서 “일일 작업 오케스트레이터”로 바뀌었습니다.
- 다만 현재 상태는 완성형이 아니라, 실패 감지 능력을 늘린 단계입니다.
- 따라서 다음 점검 포인트는 “실행기 기능 추가”가 아니라 “문서/ops 산출물의 실제 정합성 회복”입니다.
