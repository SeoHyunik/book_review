package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
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

    private static final Instant FIXED_NOW = Instant.parse("2026-03-10T12:00:00Z");

    @Mock
    private NewsEventRepository newsEventRepository;

    @InjectMocks
    private NewsQueryService newsQueryService;

    @BeforeEach
    void setUp() {
        newsQueryService.setClock(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

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

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(genericUsMarket, koreaSemiconductor));

        List<String> orderedIds = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY).stream()
                .map(item -> item.id())
                .toList();

        assertThat(orderedIds).containsExactly("korea-semiconductor", "generic-us-market");
    }

    @Test
    @DisplayName("List should prefer localized AI headline and keep summary separate")
    void getRecentNews_prefersLocalizedHeadlineAndSeparateSummary() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        NewsEvent analyzed = newsEvent(
                "localized-ko",
                "Original title",
                "Original summary",
                "Yonhap",
                "https://example.com/localized-ko",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult("Korean headline", "English headline", "Korean summary body", "English summary body"));

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(analyzed));

        var result = newsQueryService.getRecentNews().get(0);

        assertThat(result.displayTitle()).isEqualTo("Korean headline");
        assertThat(result.interpretationSummary()).isEqualTo("Korean summary body");
        assertThat(result.interpretationSummary()).isNotEqualTo(result.displayTitle());
    }

    @Test
    @DisplayName("List should fall back from missing headline to summary then macro summary")
    void getRecentNews_fallsBackToAvailableInterpretationSummary() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        NewsEvent englishFallback = newsEvent(
                "localized-fallback-en",
                "Original title",
                "Original summary",
                "Yonhap",
                "https://example.com/localized-fallback-en",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                analyzedResult(null, null, "Fallback Korean summary", null));
        NewsEvent macroFallback = newsEvent(
                "localized-fallback-macro",
                "Oil prices climb on supply concerns",
                "Energy costs moved higher.",
                "Bloomberg",
                "https://example.com/localized-fallback-macro",
                "2026-03-10T09:20:00Z",
                "2026-03-10T09:25:00Z",
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"),
                        null, null, null, null,
                        List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.9d)),
                        List.of()));

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(englishFallback, macroFallback));

        var results = newsQueryService.getRecentNews();

        assertThat(results).filteredOn(item -> item.id().equals("localized-fallback-en")).singleElement()
                .satisfies(item -> {
                    assertThat(item.displayTitle()).isEqualTo("Fallback Korean summary");
                    assertThat(item.interpretationSummary()).isEqualTo("Fallback Korean summary");
                });
        assertThat(results).filteredOn(item -> item.id().equals("localized-fallback-macro")).singleElement()
                .satisfies(item -> {
                    assertThat(item.displayTitle()).isEqualTo("Oil prices climb on supply concerns");
                    assertThat(item.interpretationSummary()).isEqualTo("OIL UP");
                });
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
    @DisplayName("Market signal overview should keep direction and semantic sentiment distinct")
    void getMarketSignalOverview_aggregatesDominantDirectionsAndSentiments() {
        NewsEvent analyzedOne = newsEvent(
                "signal-1",
                "Oil and rates move",
                "Summary",
                "Reuters",
                "https://example.com/signal-1",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"),
                        null, null, null, null,
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
                new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"),
                        null, null, null, null,
                        List.of(
                                new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.6d),
                                new MacroImpact(MacroVariable.INFLATION, ImpactDirection.DOWN, 0.6d),
                                new MacroImpact(MacroVariable.VOLATILITY, ImpactDirection.NEUTRAL, 0.5d),
                                new MacroImpact(MacroVariable.GOLD, ImpactDirection.UP, 0.6d)
                        ),
                        List.of()));

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(analyzedOne, analyzedTwo));

        var overview = newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC);

        assertThat(overview.items()).hasSize(8);
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.OIL)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.UP);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.NEGATIVE);
                    assertThat(item.sampleCount()).isEqualTo(2);
                });
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.USD)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.DOWN);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.POSITIVE);
                });
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.KOSPI)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.UP);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.POSITIVE);
                });
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.INTEREST_RATE)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.UP);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.NEUTRAL);
                });
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.GOLD)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.direction()).isEqualTo(ImpactDirection.UP);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.NEUTRAL);
                });
    }

    @Test
    @DisplayName("Recent news list should stay visible for recently ingested items even when publishedAt is older")
    void getRecentNews_usesIngestedAtForDisplayWindow() {
        Instant now = FIXED_NOW;
        NewsEvent recentlyIngested = new NewsEvent(
                "recently-ingested",
                null,
                "Older article still newly collected",
                "Summary",
                "NAVER",
                "https://example.com/recently-ingested",
                now.minus(Duration.ofDays(3)),
                now.minus(Duration.ofHours(2)),
                NewsStatus.INGESTED,
                null
        );

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(recentlyIngested));

        List<NewsListItemDto> items = newsQueryService.getRecentNews();

        assertThat(items).extracting(NewsListItemDto::id).containsExactly("recently-ingested");
    }

    @Test
    @DisplayName("Market signal overview should use analysis completion timing instead of old publishedAt")
    void getMarketSignalOverview_usesAnalysisCreatedAtForSignalWindow() {
        Instant now = FIXED_NOW;
        NewsEvent recentlyAnalyzed = new NewsEvent(
                "recently-analyzed",
                null,
                "Older article with fresh analysis",
                "Summary",
                "Reuters",
                "https://example.com/recently-analyzed",
                now.minus(Duration.ofDays(2)),
                now.minus(Duration.ofHours(2)),
                NewsStatus.ANALYZED,
                new AnalysisResult("test-model", now.minus(Duration.ofMinutes(30)),
                        null, null, null, null,
                        List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                        List.of())
        );

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(recentlyAnalyzed));

        MarketSignalOverviewDto overview = newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC);

        assertThat(overview.hasSignals()).isTrue();
        assertThat(overview.items())
                .filteredOn(item -> item.variable() == MacroVariable.USD)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.sampleCount()).isEqualTo(1);
                    assertThat(item.direction()).isEqualTo(ImpactDirection.DOWN);
                    assertThat(item.sentiment()).isEqualTo(SignalSentiment.POSITIVE);
                });
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
        return analyzedResult(null, null, null, null);
    }

    private AnalysisResult analyzedResult(String headlineKo, String headlineEn, String summaryKo, String summaryEn) {
        return new AnalysisResult("test-model", Instant.parse("2026-03-10T00:00:00Z"),
                headlineKo, headlineEn, summaryKo, summaryEn, List.of(), List.of());
    }
}
