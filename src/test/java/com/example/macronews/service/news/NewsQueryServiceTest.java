package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsQueryServiceTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @InjectMocks
    private NewsQueryService newsQueryService;

    @Test
    @DisplayName("Korea semiconductor news should rank above generic US market news")
    void getRecentNews_prioritizesKoreaSemiconductorOverGenericUsMarket() {
        NewsEvent koreaSemiconductor = newsEvent(
                "korea-semiconductor",
                "South Korea semiconductor exports rise as Samsung memory demand jumps",
                "Korea chip and memory producers benefit from export recovery.",
                "Yonhap",
                "2026-03-10T09:00:00Z");
        NewsEvent genericUsMarket = newsEvent(
                "generic-us-market",
                "US stocks close mixed ahead of earnings",
                "Wall Street indices were mixed in regular trading.",
                "Reuters",
                "2026-03-10T10:00:00Z");

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericUsMarket, koreaSemiconductor));

        List<String> orderedIds = newsQueryService.getRecentNews().stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("korea-semiconductor", "generic-us-market");
    }

    @Test
    @DisplayName("Korea trade/export news should rank above generic macro news")
    void getRecentNews_prioritizesKoreaTradeExportOverGenericMacro() {
        NewsEvent koreaTrade = newsEvent(
                "korea-trade",
                "Korea export outlook improves as trade demand recovers",
                "South Korea exporters see stronger shipments to China and the US.",
                "Korea Times",
                "2026-03-10T07:00:00Z");
        NewsEvent genericMacro = newsEvent(
                "generic-macro",
                "Global inflation expectations steady before central bank comments",
                "Investors await fresh macro signals this week.",
                "Bloomberg",
                "2026-03-10T11:00:00Z");

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericMacro, koreaTrade));

        List<String> orderedIds = newsQueryService.getRecentNews().stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("korea-trade", "generic-macro");
    }

    @Test
    @DisplayName("KOSPI headline should receive priority boost over similar generic headline")
    void getRecentNews_boostsKospiHeadline() {
        NewsEvent kospiHeadline = newsEvent(
                "kospi-boosted",
                "KOSPI rebounds on foreign buying after policy signals",
                "Foreign investors returned to Korean equities.",
                "Yonhap",
                "2026-03-10T08:00:00Z");
        NewsEvent genericKoreaHeadline = newsEvent(
                "generic-korea",
                "Korea market rebounds on foreign buying after policy signals",
                "Foreign investors returned to Korean equities.",
                "Yonhap",
                "2026-03-10T08:30:00Z");

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericKoreaHeadline, kospiHeadline));

        var results = newsQueryService.getRecentNews();

        assertThat(results.get(0).id()).isEqualTo("kospi-boosted");
        assertThat(results.get(0).priorityScore()).isGreaterThan(results.get(1).priorityScore());
    }

    @Test
    @DisplayName("Newer item should sort first when priority score is tied")
    void getRecentNews_sortsByPublishedAtWhenPriorityTied() {
        NewsEvent older = newsEvent(
                "older-korea-chip",
                "Korea semiconductor demand improves on AI orders",
                "Samsung and SK hynix benefit from chip demand.",
                "Reuters",
                "2026-03-10T05:00:00Z");
        NewsEvent newer = newsEvent(
                "newer-korea-chip",
                "Korea semiconductor demand improves on AI orders",
                "Samsung and SK hynix benefit from chip demand.",
                "Reuters",
                "2026-03-10T06:00:00Z");

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(older, newer));

        List<String> orderedIds = newsQueryService.getRecentNews().stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("newer-korea-chip", "older-korea-chip");
    }

    private NewsEvent newsEvent(String id, String title, String summary, String source, String publishedAt) {
        return new NewsEvent(
                id,
                null,
                title,
                summary,
                source,
                "https://example.com/" + id,
                Instant.parse(publishedAt),
                Instant.parse(publishedAt),
                NewsStatus.INGESTED,
                null
        );
    }
}
