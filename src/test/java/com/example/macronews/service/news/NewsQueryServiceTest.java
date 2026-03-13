package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

@ExtendWith(MockitoExtension.class)
class NewsQueryServiceTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @InjectMocks
    private NewsQueryService newsQueryService;

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Priority sort should rank Korea semiconductor news above generic US market news")
    void getRecentNews_prioritizesKoreaSemiconductorOverGenericUsMarket() {
        NewsEvent koreaSemiconductor = newsEvent(
                "korea-semiconductor",
                "South Korea semiconductor exports rise as Samsung memory demand jumps",
                "Korea chip and memory producers benefit from export recovery.",
                "Yonhap",
                "https://example.com/korea-semiconductor",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:10:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericUsMarket = newsEvent(
                "generic-us-market",
                "US stocks close mixed ahead of earnings",
                "Wall Street indices were mixed in regular trading.",
                "Reuters",
                "https://example.com/generic-us-market",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:10:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericUsMarket, koreaSemiconductor));

        List<String> orderedIds = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY).stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("korea-semiconductor", "generic-us-market");
    }

    @Test
    @DisplayName("Priority sort should rank Korea trade/export news above generic macro news")
    void getRecentNews_prioritizesKoreaTradeExportOverGenericMacro() {
        NewsEvent koreaTrade = newsEvent(
                "korea-trade",
                "Korea export outlook improves as trade demand recovers",
                "South Korea exporters see stronger shipments to China and the US.",
                "Korea Times",
                "https://example.com/korea-trade",
                "2026-03-10T07:00:00Z",
                "2026-03-10T07:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericMacro = newsEvent(
                "generic-macro",
                "Global inflation expectations steady before central bank comments",
                "Investors await fresh macro signals this week.",
                "Bloomberg",
                "https://example.com/generic-macro",
                "2026-03-10T11:00:00Z",
                "2026-03-10T11:05:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericMacro, koreaTrade));

        List<String> orderedIds = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY).stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("korea-trade", "generic-macro");
    }

    @Test
    @DisplayName("Priority sort should boost KOSPI headline over similar generic headline")
    void getRecentNews_boostsKospiHeadline() {
        NewsEvent kospiHeadline = newsEvent(
                "kospi-boosted",
                "KOSPI rebounds on foreign buying after policy signals",
                "Foreign investors returned to Korean equities.",
                "Yonhap",
                "https://example.com/kospi-boosted",
                "2026-03-10T08:00:00Z",
                "2026-03-10T08:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericKoreaHeadline = newsEvent(
                "generic-korea",
                "Korea market rebounds on foreign buying after policy signals",
                "Foreign investors returned to Korean equities.",
                "Yonhap",
                "https://example.com/generic-korea",
                "2026-03-10T08:30:00Z",
                "2026-03-10T08:35:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(genericKoreaHeadline, kospiHeadline));

        var results = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(results.get(0).id()).isEqualTo("kospi-boosted");
        assertThat(results.get(0).priorityScore()).isGreaterThan(results.get(1).priorityScore());
    }

    @Test
    @DisplayName("Priority sort should use newer publishedAt when score is tied")
    void getRecentNews_sortsByPublishedAtWhenPriorityTied() {
        NewsEvent older = newsEvent(
                "older-korea-chip",
                "Korea semiconductor demand improves on AI orders",
                "Samsung and SK hynix benefit from chip demand.",
                "Reuters",
                "https://example.com/older-korea-chip",
                "2026-03-10T05:00:00Z",
                "2026-03-10T05:10:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newer = newsEvent(
                "newer-korea-chip",
                "Korea semiconductor demand improves on AI orders",
                "Samsung and SK hynix benefit from chip demand.",
                "Reuters",
                "https://example.com/newer-korea-chip",
                "2026-03-10T06:00:00Z",
                "2026-03-10T06:10:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(older, newer));

        List<String> orderedIds = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY).stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("newer-korea-chip", "older-korea-chip");
    }

    @Test
    @DisplayName("Default list sort should be newest published first")
    void getRecentNews_defaultsToPublishedDesc() {
        NewsEvent older = newsEvent(
                "published-older",
                "Older article",
                "Older summary",
                "Reuters",
                "https://example.com/published-older",
                "2026-03-10T05:00:00Z",
                "2026-03-10T07:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newer = newsEvent(
                "published-newer",
                "Newer article",
                "Newer summary",
                "Reuters",
                "https://example.com/published-newer",
                "2026-03-10T06:00:00Z",
                "2026-03-10T06:30:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(older, newer));

        assertThat(newsQueryService.getRecentNews()).extracting(item -> item.id())
                .containsExactly("published-newer", "published-older");
    }

    @Test
    @DisplayName("Published ascending sort should show oldest first")
    void getRecentNews_supportsPublishedAscSort() {
        NewsEvent older = newsEvent(
                "published-older",
                "Older article",
                "Older summary",
                "Reuters",
                "https://example.com/published-older",
                "2026-03-10T05:00:00Z",
                "2026-03-10T07:00:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newer = newsEvent(
                "published-newer",
                "Newer article",
                "Newer summary",
                "Reuters",
                "https://example.com/published-newer",
                "2026-03-10T06:00:00Z",
                "2026-03-10T06:30:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(newer, older));

        assertThat(newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_ASC)).extracting(item -> item.id())
                .containsExactly("published-older", "published-newer");
    }

    @Test
    @DisplayName("Ingested descending sort should show most recently ingested first")
    void getRecentNews_supportsIngestedDescSort() {
        NewsEvent olderIngested = newsEvent(
                "ingested-older",
                "Same published older ingested",
                "Summary",
                "Reuters",
                "https://example.com/ingested-older",
                "2026-03-10T06:00:00Z",
                "2026-03-10T06:10:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newerIngested = newsEvent(
                "ingested-newer",
                "Same published newer ingested",
                "Summary",
                "Reuters",
                "https://example.com/ingested-newer",
                "2026-03-10T05:00:00Z",
                "2026-03-10T08:10:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(olderIngested, newerIngested));

        var results = newsQueryService.getRecentNews(null, NewsListSort.INGESTED_DESC);

        assertThat(results).extracting(item -> item.id()).containsExactly("ingested-newer", "ingested-older");
        assertThat(results.get(0).ingestedAt()).isEqualTo(Instant.parse("2026-03-10T08:10:00Z"));
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
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());
        NewsEvent analyzedWithoutUrl = newsEvent(
                "analyzed-no-url",
                "Korea auto makers watch trade talks",
                "Manufacturers monitor export conditions.",
                "Reuters",
                "",
                "2026-03-10T08:30:00Z",
                "2026-03-10T08:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());

        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(analyzedWithoutUrl, analyzedWithUrl));

        var results = newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(item -> item.status() == NewsStatus.ANALYZED);
        assertThat(results.get(0).hasAnalysis()).isTrue();
        assertThat(results.get(0).hasUrl()).isTrue();
        assertThat(results.get(1).hasUrl()).isFalse();
    }

    @Test
    @DisplayName("List should prefer Korean interpretation summary for ko locale")
    void getRecentNews_prefersKoreanInterpretationSummaryForKoLocale() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        NewsEvent analyzed = newsEvent(
                "localized-ko",
                "Korea battery export gains momentum",
                "Battery makers benefit from export demand.",
                "Yonhap",
                "https://example.com/localized-ko",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult("?쒓뎅???붿빟", "English summary"));

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(analyzed));

        var result = newsQueryService.getRecentNews().get(0);

        assertThat(result.interpretationSummary()).isEqualTo("?쒓뎅???붿빟");
    }

    @Test
    @DisplayName("List should fall back to English summary then macro summary")
    void getRecentNews_fallsBackToAvailableInterpretationSummary() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        NewsEvent englishFallback = newsEvent(
                "localized-fallback-en",
                "Korea battery export gains momentum",
                "Battery makers benefit from export demand.",
                "Yonhap",
                "https://example.com/localized-fallback-en",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult("?쒓뎅?대쭔 ?덉쓬", null));
        NewsEvent macroFallback = newsEvent(
                "localized-fallback-macro",
                "Oil prices climb on supply concerns",
                "Energy costs moved higher.",
                "Bloomberg",
                "https://example.com/localized-fallback-macro",
                "2026-03-10T09:20:00Z",
                "2026-03-10T09:25:00Z",
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"), null, null,
                        List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.9d)),
                        List.of()));

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(englishFallback, macroFallback));

        var results = newsQueryService.getRecentNews();

        assertThat(results.get(0).interpretationSummary()).isEqualTo("?쒓뎅?대쭔 ?덉쓬");
        assertThat(results.get(1).interpretationSummary()).isEqualTo("OIL UP");
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
                "2026-03-10T09:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent analyzed = newsEvent(
                "batch-2",
                "KOSPI rises on tech strength",
                "Samsung and SK hynix advanced.",
                "Reuters",
                "https://example.com/batch-2",
                "2026-03-10T09:05:00Z",
                "2026-03-10T09:06:00Z",
                NewsStatus.ANALYZED,
                analyzedResult());
        NewsEvent failed = newsEvent(
                "batch-3",
                "Oil prices climb",
                "Energy costs moved higher.",
                "Bloomberg",
                "https://example.com/batch-3",
                "2026-03-10T09:10:00Z",
                "2026-03-10T09:11:00Z",
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
        assertThat(snapshot.pendingCount()).isEqualTo(1);
        assertThat(snapshot.completed()).isFalse();
        assertThat(snapshot.items()).extracting(item -> item.id())
                .containsExactly("batch-1", "batch-2", "batch-3");
    }
    @Test
    @DisplayName("Market signal overview should aggregate dominant directions from recent analyzed news")
    void getMarketSignalOverview_aggregatesDominantDirections() {
        NewsEvent analyzedOne = newsEvent(
                "signal-1",
                "Oil and rates move",
                "Summary",
                "Reuters",
                "https://example.com/signal-1",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"), null, null,
                        List.of(
                                new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.9d),
                                new MacroImpact(MacroVariable.INTEREST_RATE, ImpactDirection.UP, 0.8d),
                                new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d),
                                new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.7d)
                        ),
                        List.of()));
        NewsEvent analyzedTwo = newsEvent(
                "signal-2",
                "More inflation pressure",
                "Summary",
                "Bloomberg",
                "https://example.com/signal-2",
                "2026-03-10T08:30:00Z",
                "2026-03-10T08:40:00Z",
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"), null, null,
                        List.of(
                                new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.6d),
                                new MacroImpact(MacroVariable.INFLATION, ImpactDirection.DOWN, 0.6d),
                                new MacroImpact(MacroVariable.VOLATILITY, ImpactDirection.NEUTRAL, 0.5d),
                                new MacroImpact(MacroVariable.GOLD, ImpactDirection.UP, 0.6d)
                        ),
                        List.of()));

        given(newsEventRepository.findTop20ByOrderByPublishedAtDesc())
                .willReturn(List.of(analyzedOne, analyzedTwo));

        var overview = newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC);

        assertThat(overview.items()).hasSize(8);
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.OIL)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.DOWN);
                    assertThat(item.sampleCount()).isEqualTo(2);
                });
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.USD)
                .singleElement()
                .satisfies(item -> assertThat(item.direction()).isEqualTo(ImpactDirection.UP));
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.VOLATILITY)
                .singleElement()
                .satisfies(item -> assertThat(item.direction()).isEqualTo(ImpactDirection.NEUTRAL));
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.KOSPI)
                .singleElement()
                .satisfies(item -> assertThat(item.direction()).isEqualTo(ImpactDirection.UP));
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.GOLD)
                .singleElement()
                .satisfies(item -> assertThat(item.direction()).isEqualTo(ImpactDirection.DOWN));
    }
    private NewsEvent newsEvent(String id, String title, String summary, String source, String url,
            String publishedAt, String ingestedAt, NewsStatus status, AnalysisResult analysisResult) {
        return new NewsEvent(
                id,
                null,
                title,
                summary,
                source,
                url,
                Instant.parse(publishedAt),
                Instant.parse(ingestedAt),
                status,
                analysisResult
        );
    }

    private AnalysisResult analyzedResult() {
        return analyzedResult(null, null);
    }

    private AnalysisResult analyzedResult(String summaryKo, String summaryEn) {
        return new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"), summaryKo, summaryEn, List.of(), List.of());
    }
}

