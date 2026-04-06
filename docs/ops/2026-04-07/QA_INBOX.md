# QA_INBOX

## Date
2026-04-07

## Raw Notes
- Carry-over from 2026-04-06 session:
  - Review the previous day's QA_STRUCTURED.md and DAILY_HANDOFF.md for unfinished items.

## 2026-04-06 실제 로그 재확인
- 참고 파일: `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md`
- 제공된 로그 파일에는 `"[NEWS-SOURCE] provider plan ..."` 이후 provider selection 흐름이 실제로 보인다.
- 제공된 로그 파일에는 `"[SCHEDULER]"` 계열 로그가 보이지 않는다. 따라서 이 캡처만으로 scheduler 진입 자체는 직접 증명되지 않는다.
- Naver는 `rawItems=5`, `parsedItems=0`, `staleItems=5`, `merged usableItems=0`로 끝났고, `stale item sample`도 반복적으로 찍혔다.
- Naver는 `ExternalApiUtils`를 통한 실제 HTTP 호출이 있었고, `X-Naver-Client-Id` / `X-Naver-Client-Secret` 헤더가 마스킹된 상태로 남았다.
- NewsAPI는 `everything-primary` 호출 중 연결 오류가 있었고, 이후 `429 received`와 `skipping call after prior 429`가 이어졌다.
- GNews는 실제 호출이 있었고, `status=400`으로 실패했다.
- selector 최종 결과는 `freshCandidates=0`, `semiFreshCandidates=0`, `finalSelection fresh=0 semiFresh=0`였다.
- ingestion 최종 결과는 `selected sourceSummary={}` 및 `batch completed requested=10 processed=0 asyncSubmitted=0`였다.
- Admin 쪽 최종 로그도 `automatic ingestion completed requested=10 returned=0 analyzed=0 pending=0 failed=0`로 끝났다.
- 해석상 이 케이스는 `provider not configured` 단독이 아니라 `configured but ineffective + compound EMPTY-result cascade`에 가깝다.
- 다만 `final freshness gate removed=...` 로그는 이 파일에서 확인되지 않았으므로, 최종 0건의 직접 기여도가 실제로 얼마나 컸는지는 별도 확인이 필요하다.

## QA Follow-up
- provider별 configured 상태, 실패 사유, final EMPTY 사유를 admin 화면과 로그에서 즉시 보이게 해야 한다.
- scheduler 진입 여부를 `runId` / `started` / `skipped reason` 단위로 더 명확히 남겨야 한다.


