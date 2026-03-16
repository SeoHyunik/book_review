package com.example.macronews.service.news.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.macronews.dto.external.ExternalNewsItem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
    @DisplayName("Selector should prefer domestic candidates during Seoul market hours without hiding fresher global news")
    void fetchTopHeadlines_prefersDomesticButStillRespectsRecency() {
        ExternalNewsItem domestic = item("domestic-1", "NAVER", "KOSPI opens higher",
                "Domestic summary", "https://domestic.example.com/1", "2026-03-13T00:00:00Z");
        ExternalNewsItem global = item("global-1", "NEWSAPI", "Fed watch update",
                "Global summary", "https://global.example.com/1", "2026-03-13T00:10:00Z");

        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true, domestic),
                provider("newsapi-global", NewsFeedPriority.FOREIGN, true, global)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("domestic-1", "global-1");
    }

    @Test
    @DisplayName("Selector should use global priority outside the domestic window")
    void fetchTopHeadlines_prefersGlobalOutsideDomesticWindow() {
        ExternalNewsItem domestic = item("domestic-1", "NAVER", "KOSPI closes lower",
                "Domestic summary", "https://domestic.example.com/1", "2026-03-12T20:00:00Z");
        ExternalNewsItem global = item("global-1", "NEWSAPI", "US inflation update",
                "Global summary", "https://global.example.com/1", "2026-03-12T19:50:00Z");

        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true, domestic),
                provider("newsapi-global", NewsFeedPriority.FOREIGN, true, global)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-12T18:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("global-1", "domestic-1");
    }

    @Test
    @DisplayName("Selector should give a bounded boost to breaking headlines")
    void fetchTopHeadlines_breakingGetsBoundedBoost() {
        ExternalNewsItem breaking = item("breaking-1", "NAVER", "\uC18D\uBCF4 KOSPI rebounds",
                "Breaking summary", "https://domestic.example.com/breaking", "2026-03-13T00:00:00Z");
        ExternalNewsItem fresher = item("fresh-1", "NAVER", "KOSPI settles after rally",
                "Fresh summary", "https://domestic.example.com/fresh", "2026-03-13T00:30:00Z");

        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true, fresher, breaking)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("breaking-1", "fresh-1");
    }

    @Test
    @DisplayName("Selector should not let stale breaking headlines outrank much newer news")
    void fetchTopHeadlines_breakingBonusStaysConservative() {
        ExternalNewsItem staleBreaking = item("breaking-1", "NAVER", "\uC18D\uBCF4 KOSPI rebounds",
                "Breaking summary", "https://domestic.example.com/breaking", "2026-03-13T00:00:00Z");
        ExternalNewsItem muchFresher = item("fresh-1", "NAVER", "KOSPI extends gains",
                "Fresh summary", "https://domestic.example.com/fresh", "2026-03-13T01:10:00Z");

        NewsSourceProviderSelector selector = selectorWithProviders(
                provider("naver", NewsFeedPriority.DOMESTIC, true, staleBreaking, muchFresher)
        );
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        List<ExternalNewsItem> ranked = selector.fetchTopHeadlines(2);

        assertThat(ranked).extracting(ExternalNewsItem::externalId)
                .containsExactly("fresh-1", "breaking-1");
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

    private StubProvider provider(String sourceCode, NewsFeedPriority priority, boolean configured,
            ExternalNewsItem... items) {
        return new StubProvider(sourceCode, priority, configured, List.of(items));
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

    private record StubProvider(
            String sourceCode,
            NewsFeedPriority priority,
            boolean configured,
            List<ExternalNewsItem> items
    ) implements NewsSourceProvider {

        @Override
        public boolean supports(NewsFeedPriority priority) {
            return this.priority == priority;
        }

        @Override
        public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
            return items.stream().limit(limit).toList();
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }
}
