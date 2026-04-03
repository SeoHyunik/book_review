# TODAY_STRATEGY

## 1. Date

2026-04-03

---

## 2. Strategy Objective

기존 방식에서 수행된 개발 상태를 현재 코드 기준으로 재정렬하고,  
중복 작업을 제거한 뒤,  
새로운 harness 기반 개발 방식으로 안전하게 전환하기 위한 첫 실행 전략을 수립한다.

오늘의 핵심 목표는:

- 이미 완료된 작업을 backlog에서 제거
- 실제 남은 작업만 추출
- 가장 안전하고 영향도 높은 첫 step을 실행 가능한 단위로 정의

---

## 3. Current Context Summary

현재 프로젝트는 기존 방식으로 상당 부분 구현이 진행된 상태이며,  
첨부된 계획 문서는 backlog source로는 유효하지만 **현재 코드 상태와 완전히 일치하지 않는다.**

다음 항목은 이미 완료된 것으로 재판정한다:

- AI 시장 요약 public access 허용
- 기본 언어 한국어 설정
- 메인 뉴스 테이블 표시 품질 일부 개선
- `/archive` 공개화 및 pagination, copy polish
- shared header/footer 브랜딩 정리
- `/news` today-only 노출 정책
- OpenAI usage 1차 정합성 정리 및 reporting cutoff
- 뉴스 수집 기본 query set 확장

또한 다음 문제가 존재한다:

- README는 최신 상태를 충분히 반영하지 못함 (archive, topic, provider 안정성 등)
- 일부 계획 문서는 이미 완료된 항목을 여전히 미완료로 간주하고 있음
- 문서 truth source가 분산되어 있음 (README vs baseline vs 실제 코드)

따라서:

> 오늘 전략은 “과거 계획”이 아니라 “현재 코드 상태”를 기준으로 수립해야 한다.

---

## 4. Inputs for Today's Planning

- PROJECT_BRIEF.md
- AGENTS.md
- DEV_LOOP.md
- HARNESS_RULES.md
- 첨부된 기존 계획 문서 (backlog source)
- 최근 작업 보고 및 reliability baseline
- 현재 코드 상태 (truth)

---

## 5. User-Observed Issues

- 일부 AI 시장 요약이 여전히 기계적인 한국어 어투를 가짐
- 확장된 뉴스 query로 인해 일부 noise 증가 가능성 존재
- README가 현재 기능 상태를 정확히 반영하지 못함
- 일부 구조(ops 문서, 전략 흐름)가 기존 방식 기준으로 남아 있음

---

## 6. Code / System Findings

- provider query는 이미 확장되어 있으며, 현재 단계는 “확장”이 아니라 “정제” 단계다
- `/news` today-only 정책은 UI 레벨에서 완료되었으나 데이터 lifecycle은 미완성
- topic pages, archive 등은 이미 존재하지만 SEO/metadata 구조는 미완성
- OpenAI usage는 내부 정합성은 맞았지만 외부 billing parity는 제한적
- 문서 계층 (README, baseline, plan)이 불일치 상태

---

## 7. Candidate Work Buckets

### Bucket A — AI Summary Tone & Readability
- 사용자 체감 품질 개선
- prompt/composer 계층 개선

### Bucket B — Ingestion Query Quality Tuning
- 확장된 query set의 noise 정제
- provider 안정성 유지하면서 품질 개선

### Bucket C — News Retention Policy Decision
- today-only vs archive vs delete 정책 충돌 해결

### Bucket D — Partial Update Pilot
- Thymeleaf 기반 부분 렌더링 실험

### Bucket E — Admin Usage Follow-up
- usage parity 및 성능 개선

### Bucket F — SEO Foundation Expansion
- metadata, canonical, sitemap 등 기본기 구축

---

## 8. Priority Order

1. Bucket B — Query Quality Tuning
2. Bucket A — AI Summary Tone
3. Bucket F — SEO Foundation
4. Bucket D — Partial Update Pilot
5. Bucket C — Retention Policy
6. Bucket E — Admin Usage Follow-up

우선순위 기준:

- change surface 최소
- production 영향 최소
- 사용자 체감 효과 존재

---

## 9. Selected Work for Today

### Bucket B — Ingestion Query Quality Tuning

선정 이유:

- 이미 query 확장은 완료된 상태 → 다음 자연스러운 단계
- 코드 영향 범위 제한 가능
- 다른 영역과 충돌 없음
- harness 첫 step으로 적합

오늘 목표:

- query set 내 broad token noise를 줄이는 최소 수정 수행

---

## 10. Step Breakdown

### Step 1. Query Noise Tuning (First Harness Step)

**Goal**
- 확장된 뉴스 query set에서 불필요한 noise를 줄인다

**Target Area**
- provider query / default query resolution

**Likely Files**
- NaverNewsSourceProvider
- NewsApiServiceImpl
- GNewsSourceProvider
- application.yaml
- provider 관련 테스트 코드

**Forbidden Scope**
- selector refactor
- ranking policy 변경
- UI 변경
- AI summary logic 변경
- retention policy 변경

**Validation**
- fallback 동작 유지
- query resolution deterministic 유지
- provider 테스트 통과
- 결과 뉴스 품질에서 명백한 noise 감소

**Expected Output**
- query 조건 소폭 수정
- 필요 시 테스트 보완
- 변경 범위 최소화

---

## 11. Recommended Agent Flow

기본 순서:

1. navi → 현재 query 흐름 분석
2. reviewer → 위험성 및 영향 범위 확인
3. worker → 최소 수정 구현
4. reviewer → 검증
5. dockeeper → 문서 영향 여부 확인
6. gitter → commit 준비

---

## 12. Codex Execution Notes

- 항상 다음 문서를 먼저 읽는다:
    - PROJECT_BRIEF.md
    - AGENTS.md
    - HARNESS_RULES.md
    - DEV_LOOP.md

- 반드시 현재 날짜 폴더 사용:
    - docs/ops/2026-04-03/

- 절대 다음을 하지 않는다:
    - format 파일 수정
    - 다른 bucket 작업 혼합
    - 불필요한 refactor

- step 단위로만 작업

---

## 13. Risks and Constraints

- query 변경이 provider 결과를 과도하게 제한할 가능성
- 테스트가 실제 production data를 완전히 반영하지 않을 수 있음
- noise 감소가 정보 손실로 이어질 수 있음
- provider API 특성 차이로 인해 일관성 유지 어려움

---

## 14. Deferrals

오늘 하지 않는 것:

- AI summary tone 개선
- SEO 구조 개선
- partial update
- retention 정책 결정
- admin usage 추가 작업

이유:
- scope 분리
- step isolation 유지
- 하네스 첫 실행 안정성 확보

---

## 15. Definition of Done for Today

- query noise tuning step이 안전하게 완료됨
- 불필요한 파일 변경 없음
- provider 테스트 정상 통과
- 변경 영향 명확히 설명됨
- 다음 step이 명확하게 정의됨

---

## 16. Handoff Requirement

작업 종료 후 반드시 다음 파일 생성:

- docs/ops/2026-04-03/DAILY_HANDOFF.md

포함 내용:

- 완료된 step
- 부분 완료 작업
- 발견된 리스크
- harness 개선 포인트
- 다음 추천 step