package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NewsSourceProviderSelectorTest {

    @Test
    @DisplayName("Selector should choose domestic provider during Seoul domestic window")
    void selectCurrentProvider_choosesDomesticProviderInsideWindow() {
        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true),
                provider("newsapi-global", NewsFeedPriority.FOREIGN, true)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        assertThat(selector.selectCurrentProvider()).get()
                .extracting(NewsSourceProvider::sourceCode)
                .isEqualTo("naver");
    }

    @Test
    @DisplayName("Selector should fall back to global provider when domestic provider is disabled")
    void selectCurrentProvider_fallsBackToGlobalWhenDomesticUnavailable() {
        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, false),
                provider("newsapi-global", NewsFeedPriority.FOREIGN, true)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        assertThat(selector.selectCurrentProvider()).get()
                .extracting(NewsSourceProvider::sourceCode)
                .isEqualTo("newsapi-global");
    }

    @Test
    @DisplayName("Selector should stop early when enough fresh items are found from early providers")
    void fetchTopHeadlines_stopsEarlyWhenEnoughFreshItemsExist() {
        StubProvider newsApi = provider("newsapi-global", NewsFeedPriority.FOREIGN, true,
                List.of(
                        item("fresh-1", "NEWSAPI", "Fed update", "Summary", "https://global.example.com/1", "2026-03-13T00:10:00Z"),
                        item("fresh-2", "NEWSAPI", "Inflation update", "Summary", "https://global.example.com/2", "2026-03-13T00:05:00Z")
                ),
                List.of());
        StubProvider gnews = provider("gnews-global", NewsFeedPriority.FOREIGN, true,
                List.of(item("unused", "GNEWS", "Unused", "Summary", "https://gnews.example.com/1", "2026-03-13T00:01:00Z")),
                List.of());

        NewsSourceProviderSelector selector = selectorWithProviders(newsApi, gnews);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-12T18:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("fresh-1", "fresh-2");
        assertThat(newsApi.freshCalls()).isEqualTo(1);
        assertThat(gnews.freshCalls()).isZero();
        assertThat(gnews.semiFreshCalls()).isZero();
    }

    @Test
    @DisplayName("Selector should use semi-fresh items only to fill the remaining slots")
    void fetchTopHeadlines_usesSemiFreshOnlyForRemainingSlots() {
        StubProvider newsApi = provider("newsapi-global", NewsFeedPriority.FOREIGN, true,
                List.of(item("fresh-1", "NEWSAPI", "Fed update", "Summary", "https://global.example.com/1", "2026-03-13T00:10:00Z")),
                List.of(item("semi-1", "NEWSAPI", "Earlier recap", "Summary", "https://global.example.com/2", "2026-03-12T18:10:00Z")));
        StubProvider gnews = provider("gnews-global", NewsFeedPriority.FOREIGN, true,
                List.of(item("fresh-2", "GNEWS", "Oil update", "Summary", "https://gnews.example.com/1", "2026-03-13T00:07:00Z")),
                List.of(item("semi-2", "GNEWS", "Earlier oil recap", "Summary", "https://gnews.example.com/2", "2026-03-12T18:00:00Z")));

        NewsSourceProviderSelector selector = selectorWithProviders(newsApi, gnews);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-12T18:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(3);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("fresh-1", "fresh-2", "semi-1");
    }

    @Test
    @DisplayName("Selector should not replace fresh items with semi-fresh ones")
    void fetchTopHeadlines_doesNotLetSemiFreshReplaceFresh() {
        StubProvider newsApi = provider("newsapi-global", NewsFeedPriority.FOREIGN, true,
                List.of(
                        item("fresh-1", "NEWSAPI", "Fresh one", "Summary", "https://global.example.com/1", "2026-03-13T00:10:00Z"),
                        item("fresh-2", "NEWSAPI", "Fresh two", "Summary", "https://global.example.com/2", "2026-03-13T00:08:00Z")
                ),
                List.of(item("semi-1", "NEWSAPI", "Semi fresh", "Summary", "https://global.example.com/3", "2026-03-12T18:10:00Z")));

        NewsSourceProviderSelector selector = selectorWithProviders(newsApi);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-12T18:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("fresh-1", "fresh-2");
    }

    @Test
    @DisplayName("Selector should deduplicate across fresh and semi-fresh provider buckets")
    void fetchTopHeadlines_deduplicatesAcrossProvidersAndBuckets() {
        StubProvider newsApi = provider("newsapi-global", NewsFeedPriority.FOREIGN, true,
                List.of(item("dup", "NEWSAPI", "Shared item", "Summary", "https://shared.example.com/item", "2026-03-13T00:10:00Z")),
                List.of());
        StubProvider gnews = provider("gnews-global", NewsFeedPriority.FOREIGN, true,
                List.of(),
                List.of(item("dup-semi", "GNEWS", "Shared item", "Summary", "https://shared.example.com/item", "2026-03-12T18:10:00Z")));

        NewsSourceProviderSelector selector = selectorWithProviders(newsApi, gnews);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-12T18:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::url)
                .containsExactly("https://shared.example.com/item");
    }

    @Test
    @DisplayName("Selector should prefer domestic candidates during Seoul market hours without hiding fresher global news")
    void fetchTopHeadlines_prefersDomesticButStillRespectsRecency() {
        ExternalNewsItem domestic = item("domestic-1", "NAVER", "KOSPI opens higher",
                "Domestic summary", "https://domestic.example.com/1", "2026-03-13T00:00:00Z");
        ExternalNewsItem global = item("global-1", "NEWSAPI", "Fed watch update",
                "Global summary", "https://global.example.com/1", "2026-03-13T00:10:00Z");

        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true, List.of(domestic), List.of()),
                provider("newsapi-global", NewsFeedPriority.FOREIGN, true, List.of(global), List.of())
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("domestic-1", "global-1");
    }

    private NewsSourceProviderSelector selectorWithProviders(StubProvider... providers) {
        NewsSourceProviderSelector selector = new NewsSourceProviderSelector(List.of(providers));
        ReflectionTestUtils.setField(selector, "businessTimezone", "Asia/Seoul");
        ReflectionTestUtils.setField(selector, "domesticStartHour", 5);
        ReflectionTestUtils.setField(selector, "domesticEndHour", 22);
        ReflectionTestUtils.setField(selector, "breakingBonusMinutes", 45);
        ReflectionTestUtils.setField(selector, "preferredSourceBonusMinutes", 15);
        return selector;
    }

    private StubProvider provider(String sourceCode, NewsFeedPriority priority, boolean configured) {
        return new StubProvider(sourceCode, priority, configured, List.of(), List.of());
    }

    private StubProvider provider(String sourceCode, NewsFeedPriority priority, boolean configured,
            List<ExternalNewsItem> freshItems, List<ExternalNewsItem> semiFreshItems) {
        return new StubProvider(sourceCode, priority, configured, freshItems, semiFreshItems);
    }

    private ExternalNewsItem item(String externalId, String source, String title, String summary, String url,
            String publishedAt) {
        return new ExternalNewsItem(
                externalId,
                source,
                title,
                summary,
                url,
                Instant.parse(publishedAt)
        );
    }

    private static final class StubProvider implements NewsSourceProvider {

        private final String sourceCode;
        private final NewsFeedPriority priority;
        private final boolean configured;
        private final List<ExternalNewsItem> freshItems;
        private final List<ExternalNewsItem> semiFreshItems;
        private final List<NewsFreshnessBucket> calls = new ArrayList<>();

        private StubProvider(String sourceCode, NewsFeedPriority priority, boolean configured,
                List<ExternalNewsItem> freshItems, List<ExternalNewsItem> semiFreshItems) {
            this.sourceCode = sourceCode;
            this.priority = priority;
            this.configured = configured;
            this.freshItems = freshItems;
            this.semiFreshItems = semiFreshItems;
        }

        @Override
        public String sourceCode() {
            return sourceCode;
        }

        @Override
        public boolean supports(NewsFeedPriority priority) {
            return this.priority == priority;
        }

        @Override
        public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
            return fetchTopHeadlines(limit, NewsFreshnessBucket.FRESH);
        }

        @Override
        public List<ExternalNewsItem> fetchTopHeadlines(int limit, NewsFreshnessBucket bucket) {
            calls.add(bucket);
            List<ExternalNewsItem> items = bucket == NewsFreshnessBucket.FRESH ? freshItems : semiFreshItems;
            return items.stream().limit(limit).toList();
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        private int freshCalls() {
            return (int) calls.stream().filter(bucket -> bucket == NewsFreshnessBucket.FRESH).count();
        }

        private int semiFreshCalls() {
            return (int) calls.stream().filter(bucket -> bucket == NewsFreshnessBucket.SEMI_FRESH).count();
        }
    }
}
