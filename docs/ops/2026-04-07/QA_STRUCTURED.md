# QA_STRUCTURED

## Date
2026-04-07

## 구현 우선 항목

### Item 1. 자동 수집 실행 경로의 zero-result 원인을 한 번에 추적할 수 있어야 함
- category: reliability
- surface: admin automatic ingestion, scheduler entry, provider selection, Naver News integration, NewsAPI, GNews, admin logs/UI
- symptom: 실행은 provider plan까지 보이지만, scheduler 진입 여부와 각 provider 실패 사유가 분리되어 보이지 않는다. 현재 로그에는 `parsedItems=0`, Naver 헤더 누락, NewsAPI `429`, GNews `400`, `freshCandidates=0`, `semiFreshCandidates=0`, `selected sourceSummary={}`, `returned=0`, `analyzed=0`가 함께 나타나서 zero-result가 provider 설정 문제인지, upstream 거절인지, freshness filtering인지 바로 판단하기 어렵다
- requested change: `runId`, `started`, `skipped reason`를 scheduler 진입 시점에 남기고, provider별 configured 상태와 failure reason을 노출하며, selector 결과와 최종 EMPTY reason을 admin 화면과 로그에서 바로 확인할 수 있게 정리한다
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

## 제품 결정 필요 항목

### Item 2. final freshness gate 제거 상태를 유지할지 결정 필요
- category: product-decision
- surface: ingestion selection policy, freshness gate, fallback selection
- symptom: 로그에 `final freshness gate removed=...`가 남아 있는데도 최종 결과는 계속 zero usable items로 끝난다. 현재 상태가 의도된 동작인지, 아니면 후속 fallback이나 다른 selection rule이 필요한지 판단이 불명확하다
- requested change: final freshness gate를 유지 제거할지, 복구할지, 또는 더 명확한 selection rule로 대체할지 결정한다. 이 결정이 끝나기 전에는 현재 zero-result cascade를 정상 동작으로 확정하면 안 된다
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes
