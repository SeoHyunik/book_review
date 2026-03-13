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
        NewsSourceProviderSelector selector = new NewsSourceProviderSelector(List.of(
                new StubProvider("naver", NewsFeedPriority.DOMESTIC, true),
                new StubProvider("newsapi-global", NewsFeedPriority.FOREIGN, true)
        ));
        ReflectionTestUtils.setField(selector, "domesticStartHour", 5);
        ReflectionTestUtils.setField(selector, "domesticEndHour", 22);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        assertThat(selector.selectCurrentProvider()).get().extracting(NewsSourceProvider::sourceCode)
                .isEqualTo("naver");
    }

    @Test
    @DisplayName("Selector should fall back to global provider when domestic provider is disabled")
    void selectCurrentProvider_fallsBackToGlobalWhenDomesticUnavailable() {
        NewsSourceProviderSelector selector = new NewsSourceProviderSelector(List.of(
                new StubProvider("naver", NewsFeedPriority.DOMESTIC, false),
                new StubProvider("newsapi-global", NewsFeedPriority.FOREIGN, true)
        ));
        ReflectionTestUtils.setField(selector, "domesticStartHour", 5);
        ReflectionTestUtils.setField(selector, "domesticEndHour", 22);
        ReflectionTestUtils.setField(selector, "clock",
                Clock.fixed(Instant.parse("2026-03-13T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        assertThat(selector.selectCurrentProvider()).get().extracting(NewsSourceProvider::sourceCode)
                .isEqualTo("newsapi-global");
    }

    private record StubProvider(String sourceCode, NewsFeedPriority priority, boolean configured)
            implements NewsSourceProvider {

        @Override
        public boolean supports(NewsFeedPriority priority) {
            return this.priority == priority;
        }

        @Override
        public List<ExternalNewsItem> fetchTopHeadlines(int limit) {
            return List.of();
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }
}
