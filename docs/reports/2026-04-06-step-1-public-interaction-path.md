# 2026-04-06 Step 1 분석 노트: 공개 상호작용 경로 추적

## 범위
- Step 1의 목표는 첫 진입 경로, 메인 뉴스 목록, 공통 헤더/버튼/푸터가 어떤 controller/template 흐름으로 렌더되는지 확인하는 것이다.
- 이번 단계에서는 코드 변경을 하지 않았다.

## 확인한 공개 경로
- `/` 진입은 [`PageController`](/S:/dev/project/auziraum/src/main/java/com/example/macronews/controller/PageController.java)에서 `redirect:/news`로 연결된다.
- `/news` 목록은 [`NewsController.list()`](S:/dev/project/auziraum/src/main/java/com/example/macronews/controller/NewsController.java)에서 렌더되며, `news/list` 템플릿을 반환한다.
- 상세 진입은 같은 컨트롤러의 `@GetMapping("/{id}")`가 담당하며, 비회원 접근은 `AnonymousDetailViewGateService`로 추가 제어된다.

## 서비스 흐름
- 뉴스 목록 데이터는 [`NewsQueryService`](S:/dev/project/auziraum/src/main/java/com/example/macronews/service/news/NewsQueryService.java)의 `getRecentNewsForToday()`와 `getMarketSignalOverview()`에서 온다.
- 메인 목록 상단의 피처드 요약은 `MarketSummarySnapshotService -> AiMarketSummaryService -> RecentMarketSummaryService` 순으로 fallback 된다.
- 상단 시장 전망 스냅샷은 `MarketForecastQueryService.getCurrentSnapshot()`에서 공급된다.

## 공통 UI 경로
- 공통 헤더/언어 전환/로그인 버튼/관리자 버튼/푸터는 [`templates/fragments/layout.html`](S:/dev/project/auziraum/src/main/resources/templates/fragments/layout.html)에 집중되어 있다.
- 현재 경로와 쿼리 파라미터 상태는 [`GlobalUiModelAttributes`](S:/dev/project/auziraum/src/main/java/com/example/macronews/controller/GlobalUiModelAttributes.java)가 모델에 주입한다.
- 메인 뉴스 테이블, 필터 버튼, 정렬 버튼, 페이지네이션, 관리자 일괄 삭제 액션은 [`templates/news/list.html`](S:/dev/project/auziraum/src/main/resources/templates/news/list.html)에 있다.

## Step 1 기준 판단
- 공개 상호작용 경로는 controller -> service -> template 순으로 분리되어 있어, 현재 확인된 구조만으로는 레이어 경계 위반이 보이지 않는다.
- `/news`는 조회 실패 시 빈 목록/부분 대체값으로 내려가도록 방어 코드가 들어 있어, 공개 페이지를 hard fail 시키지 않으려는 방향과 맞는다.
- 다만 `layout.html`의 언어 전환 form과 `GlobalUiModelAttributes`가 현재 path/query 상태를 재사용하는 구조라서, 이후 UI 수정 시 공통 영향 범위를 반드시 확인해야 한다.

## 이번 단계에서 하지 않은 것
- query/ranking 로직 변경
- DB schema 변경
- 템플릿/스타일 변경
- 다른 단계로의 확장

## 검증
- controller/template/service 연결만 정적 추적으로 확인했다.
- 실행 테스트나 렌더링 검증은 수행하지 않았다.

## 다음에 볼 수 있는 후보
- Step 2의 최소 공개 UI 수정 대상은 `templates/news/list.html`과 `templates/fragments/layout.html` 중심이 된다.
