# 2026-04-13 Step 1: Provider Failure Boundary Reconfirm

## 범위
- 분석 전용.
- 애플리케이션 코드는 변경하지 않았다.
- Step 1의 목적은 Naver ingestion이 실제로 어디서 최초로 0 usable item으로 떨어지는지 다시 확인하는 것이다.

## 확인한 실행 경로
- 스케줄러 진입점은 [`ScheduledNewsIngestionJob.java`](../main/java/com/example/macronews/config/ScheduledNewsIngestionJob.java) 이다.
- 관리자 수동 ingestion도 동일한 서비스 경로로 들어가며 [`AdminNewsController.java`](../main/java/com/example/macronews/controller/AdminNewsController.java) 에서 `newsIngestionService.ingestTopHeadlines(...)` 를 호출한다.
- batch ingest의 실제 선택/필터링은 [`NewsIngestionServiceImpl.java`](../main/java/com/example/macronews/service/news/NewsIngestionServiceImpl.java) 의 `loadScheduledHeadlineFeed(...)` 와 후속 freshness gate 에서 일어난다.
- provider 집계와 zero-result summary 는 [`NewsSourceProviderSelector.java`](../main/java/com/example/macronews/service/news/source/NewsSourceProviderSelector.java) 가 담당한다.
- Naver 파싱, stale 제거, relevance 필터링, empty reason 기록은 [`NaverNewsSourceProvider.java`](../main/java/com/example/macronews/service/news/source/NaverNewsSourceProvider.java) 에 있다.

## 재확인 결과
- scheduler와 admin controller는 ingestion 시작 여부만 결정한다.
- selector는 provider 결과를 모으고, 최종적으로 비어 있으면 `no-candidates-after-provider-filters` 경고를 남긴다.
- 최초의 실질적 실패 경계는 Naver provider 내부이다.
- Naver provider에서 stale-date check, null/invalid publish date, relevance filtering, usable link 부족, empty title 등의 이유로 candidate가 제거될 수 있다.
- Naver가 usable item을 내지 못하면 selector는 `freshCandidates=0`, `semiFreshCandidates=0` 상태가 되고, ingestion service는 empty selection 을 받는다.

## 코드와 로그가 일치하는 지점
- provider가 `parsedItems=0` 과 `provider empty reason=...` 을 남기는 패턴은 upstream 필터링이 먼저 무너졌다는 뜻이다.
- selector의 zero-result summary 는 결과 요약일 뿐, 최초 원인이 아니다.
- service의 final freshness gate 는 downstream 보정이다.

## 검증
- 위 5개 코드 경로를 직접 대조했다.
- `docs/reports/2026-04-06-admin-auto-ingestion-real-logs.md` 의 실제 로그와도 비교했다.

## 변경 없음
- freshness threshold 는 변경하지 않았다.
- provider ranking 또는 selector 로직은 변경하지 않았다.
- controller contract 는 변경하지 않았다.
- 테스트는 수정하지 않았다.
- ops 문서는 수정하지 않았다.

## 리스크
- 현재 상태만으로는 provider-side recovery 가 필요한지, 아니면 데이터 소스 자체가 지속적으로 stale 인지 분리되지 않는다.
- selector/controller 로 문제를 옮기면 실패 경계를 흐릴 가능성이 있다.

## 다음 가능한 단계
- Step 2 에서 provider-side 의 최소 복구 수정이 필요하다면, Naver provider 내부의 가장 작은 safe change 를 적용한다.
