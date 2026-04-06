# 2026-04-06 Step 1 Analysis: Public Interaction Path

## Scope
- Verify the public-facing route flow from `/` to `/news` and into `/news/{id}`.
- Confirm which controller, service, and template files participate in the shared UI shell.
- Check whether the current implementation fails soft or hard on public pages.

## Verified Path
- `PageController.home()` redirects `/` to `/news` in `src/main/java/com/example/macronews/controller/PageController.java:11-14`.
- `NewsController.list()` renders the news index at `/news`, assembles list data, market overview, forecast snapshot, and featured summary state, then returns `news/list` in `src/main/java/com/example/macronews/controller/NewsController.java:48-93`.
- `NewsController.detail()` renders `/news/{id}`, blocks anonymous access through `AnonymousDetailViewGateService`, and returns `news/detail` when the detail page is allowed in `src/main/java/com/example/macronews/controller/NewsController.java:161-200`.

## Shared UI Shell
- `GlobalUiModelAttributes` injects `currentPath`, `currentStatus`, `currentSort`, `currentPage`, and `currentLang` for all `@Controller` handlers in `src/main/java/com/example/macronews/controller/GlobalUiModelAttributes.java:10-19`.
- `templates/fragments/layout.html` uses `currentPath` as the language-switch form action and preserves `status`, `sort`, and `page` via hidden inputs in `src/main/resources/templates/fragments/layout.html:63-71`.
- Both `news/list.html` and `news/detail.html` are mounted inside the same layout fragment in `src/main/resources/templates/news/list.html:3` and `src/main/resources/templates/news/detail.html:3`.

## Fail-Soft Behavior
- The news list path catches `RuntimeException` for list data, market signal overview, forecast snapshot, and featured summary resolution, then falls back to empty or null-safe values in `src/main/java/com/example/macronews/controller/NewsController.java:96-156`.
- The detail path redirects to `/news` when the ID is missing or when an exception occurs, rather than hard failing the page in `src/main/java/com/example/macronews/controller/NewsController.java:169-204`.

## Gate Behavior
- `AnonymousDetailViewGateService.canAccess()` and `recordAccess()` use a session-backed ID set to enforce the free-detail-view limit in `src/main/java/com/example/macronews/service/auth/AnonymousDetailViewGateService.java:13-44`.
- Anonymous users who exceed the limit are redirected to `/login` with `continue` and `gated=1` query parameters in `src/main/java/com/example/macronews/controller/NewsController.java:175-178`.

## Conclusion
- The current public interaction path is controller -> service -> template, with one shared layout layer and a session-based anonymous detail gate.
- `/news` is already protected against hard failure by explicit fallback branches.
- The main verified blast radius for Step 2 remains `templates/fragments/layout.html`, `templates/news/list.html`, and the copy/model values supplied by `NewsController`.
