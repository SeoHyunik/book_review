package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.AnalysisResult;
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
                "https://example.com/korea-semiconductor",
                "2026-03-10T09:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericUsMarket = newsEvent(
                "generic-us-market",
                "US stocks close mixed ahead of earnings",
                "Wall Street indices were mixed in regular trading.",
                "Reuters",
                "https://example.com/generic-us-market",
                "2026-03-10T10:00:00Z",
                NewsStatus.INGESTED,
                null);

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
                "https://example.com/korea-trade",
                "2026-03-10T07:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericMacro = newsEvent(
                "generic-macro",
                "Global inflation expectations steady before central bank comments",
                "Investors await fresh macro signals this week.",
                "Bloomberg",
                "https://example.com/generic-macro",
                "2026-03-10T11:00:00Z",
                NewsStatus.INGESTED,
                null);

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
                "https://example.com/kospi-boosted",
                "2026-03-10T08:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericKoreaHeadline = newsEvent(
                "generic-korea",
                "Korea market rebounds on foreign buying after policy signals",
                "Foreign investors returned to Korean equities.",
                "Yonhap",
                "https://example.com/generic-korea",
                "2026-03-10T08:30:00Z",
                NewsStatus.INGESTED,
                null);

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
                "https://example.com/older-korea-chip",
                "2026-03-10T05:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newer = newsEvent(
                "newer-korea-chip",
                "Korea semiconductor demand improves on AI orders",
                "Samsung and SK hynix benefit from chip demand.",
                "Reuters",
                "https://example.com/newer-korea-chip",
                "2026-03-10T06:00:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(older, newer));

        List<String> orderedIds = newsQueryService.getRecentNews().stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("newer-korea-chip", "older-korea-chip");
    }

    @Test
    @DisplayName("Status filter should use matching repository query and expose cheap signals")
    void getRecentNews_filtersByStatusAndExposesSignals() {
        NewsEvent analyzedWithUrl = newsEvent(
                "analyzed-news",
                "Korea battery export gains momentum",
                "Battery makers benefit from export demand.",
                "Yonhap",
                "https://example.com/analyzed-news",
                "2026-03-10T09:30:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());
        NewsEvent analyzedWithoutUrl = newsEvent(
                "analyzed-no-url",
                "Korea auto makers watch trade talks",
                "Manufacturers monitor export conditions.",
                "Reuters",
                "",
                "2026-03-10T08:30:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());

        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(analyzedWithoutUrl, analyzedWithUrl));

        var results = newsQueryService.getRecentNews(NewsStatus.ANALYZED);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(item -> item.status() == NewsStatus.ANALYZED);
        assertThat(results.get(0).hasAnalysis()).isTrue();
        assertThat(results.get(0).hasUrl()).isTrue();
        assertThat(results.get(1).hasUrl()).isFalse();
    }

    @Test
    @DisplayName("Auto ingestion snapshot should summarize current batch status counts")
    void getAutoIngestionBatchStatus_summarizesBatch() {
        NewsEvent ingested = newsEvent(
                "batch-1",
                "Korea export data improves",
                "Exports picked up this month.",
                "Yonhap",
                "https://example.com/batch-1",
                "2026-03-10T09:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent analyzed = newsEvent(
                "batch-2",
                "KOSPI rises on tech strength",
                "Samsung and SK hynix advanced.",
                "Reuters",
                "https://example.com/batch-2",
                "2026-03-10T09:05:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());
        NewsEvent failed = newsEvent(
                "batch-3",
                "Oil prices climb",
                "Energy costs moved higher.",
                "Bloomberg",
                "https://example.com/batch-3",
                "2026-03-10T09:10:00Z",
                NewsStatus.FAILED,
                null);

        given(newsEventRepository.findAllById(List.of("batch-1", "batch-2", "batch-3")))
                .willReturn(List.of(analyzed, failed, ingested));

        var snapshot = newsQueryService.getAutoIngestionBatchStatus(10, 3, List.of("batch-1", "batch-2", "batch-3"));

        assertThat(snapshot.requestedCount()).isEqualTo(10);
        assertThat(snapshot.returnedCount()).isEqualTo(3);
        assertThat(snapshot.ingestedCount()).isEqualTo(1);
        assertThat(snapshot.analyzedCount()).isEqualTo(1);
        assertThat(snapshot.failedCount()).isEqualTo(1);
        assertThat(snapshot.items()).extracting(item -> item.id())
                .containsExactly("batch-1", "batch-2", "batch-3");
    }

    private NewsEvent newsEvent(String id, String title, String summary, String source, String url,
            String publishedAt, NewsStatus status, AnalysisResult analysisResult) {
        return new NewsEvent(
                id,
                null,
                title,
                summary,
                source,
                url,
                Instant.parse(publishedAt),
                Instant.parse(publishedAt),
                status,
                analysisResult
        );
    }

    private AnalysisResult analyzedResult() {
        return new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"), List.of(), List.of());
    }
}
