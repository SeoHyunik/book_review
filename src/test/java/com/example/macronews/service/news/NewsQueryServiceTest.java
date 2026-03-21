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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("Priority sort should rank market moving macro news above generic finance coverage")
    void getRecentNews_prioritizesMarketMovingMacroNews() {
        NewsEvent highImpactMacro = newsEvent(
                "high-impact-macro",
                "Fed rate decision lifts Treasury yields after hotter CPI report",
                "FOMC officials signaled a tighter policy path as inflation stayed elevated.",
                "Reuters",
                "https://example.com/high-impact-macro",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:10:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent genericFinance = newsEvent(
                "generic-finance",
                "Bank shares advance after earnings update",
                "Financial stocks moved higher in routine trading.",
                "Reuters",
                "https://example.com/generic-finance",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:10:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(genericFinance, highImpactMacro));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("high-impact-macro", "generic-finance");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Priority score should deterministically boost high impact market keywords")
    void getRecentNews_boostsHighImpactMarketKeywordsDeterministically() {
        NewsEvent boosted = newsEvent(
                "boosted",
                "BOJ signals rate decision shift as yen and bond yields jump",
                "Foreign exchange markets reacted after central bank guidance.",
                "Bloomberg",
                "https://example.com/boosted",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:40:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent baseline = newsEvent(
                "baseline",
                "Corporate strategy update draws investor attention",
                "Executives outlined a medium-term business plan.",
                "Bloomberg",
                "https://example.com/baseline",
                "2026-03-10T09:20:00Z",
                "2026-03-10T09:25:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(boosted, baseline));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("boosted", "baseline");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Trusted source should provide a modest edge for otherwise similar articles")
    void getRecentNews_prioritizesTrustedSourceWhenContentIsSimilar() {
        NewsEvent trusted = newsEvent(
                "trusted",
                "Treasury yields rise after inflation update",
                "Market participants reacted to the latest CPI figures.",
                "Reuters",
                "https://www.reuters.com/markets/trusted",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent neutral = newsEvent(
                "neutral",
                "Treasury yields rise after inflation update",
                "Market participants reacted to the latest CPI figures.",
                "MarketWatcher",
                "https://example.com/markets/neutral",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:06:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(neutral, trusted));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("trusted", "neutral");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Missing source should remain neutral and safe")
    void getRecentNews_handlesMissingSourceNeutrally() {
        NewsEvent missingSource = newsEvent(
                "missing-source",
                "Treasury yields rise after inflation update",
                "Market participants reacted to the latest CPI figures.",
                null,
                "https://example.com/markets/missing-source",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:05:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(missingSource));

        List<NewsListItemDto> items = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("missing-source");
            assertThat(item.priorityScore()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    @DisplayName("Strong market content should outrank source advantage alone")
    void getRecentNews_keepsContentAsPrimarySignalOverSourceWeight() {
        NewsEvent strongContentNeutralSource = newsEvent(
                "strong-neutral-source",
                "Fed rate decision lifts Treasury yields after hotter CPI report",
                "FOMC officials signaled a tighter policy path as inflation stayed elevated.",
                "MarketPulse",
                "https://example.com/strong-neutral-source",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent weakerTrustedSource = newsEvent(
                "weaker-trusted-source",
                "Company shares move after executive comments",
                "Traders watched a routine management update.",
                "Bloomberg",
                "https://www.bloomberg.com/weaker-trusted-source",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:05:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(weakerTrustedSource, strongContentNeutralSource));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("strong-neutral-source", "weaker-trusted-source");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Priority sort should demote generic low signal articles below market relevant ones")
    void getRecentNews_demotesGenericLowSignalArticle() {
        NewsEvent marketRelevant = newsEvent(
                "market-relevant",
                "ECB rate decision pushes euro and bond yields higher",
                "Inflation data and central bank guidance moved markets.",
                "Reuters",
                "https://example.com/market-relevant",
                "2026-03-10T09:00:00Z",
                "2026-03-10T09:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent lowSignal = newsEvent(
                "low-signal",
                "Best way to enjoy a spring festival event this weekend",
                "Lifestyle tips and a giveaway guide for visitors.",
                "Example Life",
                "https://example.com/low-signal",
                "2026-03-10T10:00:00Z",
                "2026-03-10T10:05:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(lowSignal, marketRelevant));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("market-relevant", "low-signal");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Priority score should deterministically demote noisy phrases")
    void getRecentNews_demotesNoisyPhraseDeterministically() {
        NewsEvent noisy = newsEvent(
                "noisy",
                "Shocking celebrity buzz draws attention at opening event",
                "A giveaway and fashion promotion became a hot issue online.",
                "Example Buzz",
                "https://example.com/noisy",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:35:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent baseline = newsEvent(
                "baseline",
                "Corporate strategy update draws investor attention",
                "Executives outlined a medium-term business plan.",
                "Bloomberg",
                "https://example.com/baseline",
                "2026-03-10T09:20:00Z",
                "2026-03-10T09:25:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(noisy, baseline));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("baseline", "noisy");
        assertThat(orderedItems.get(1).priorityScore()).isLessThan(orderedItems.get(0).priorityScore());
    }

    @Test
    @DisplayName("Strong market signals should outweigh weak noisy phrases")
    void getRecentNews_doesNotOverDemoteStrongMarketSignals() {
        NewsEvent strongButNoisy = newsEvent(
                "strong-but-noisy",
                "Fed rate decision becomes hot issue as Treasury yields surge",
                "CPI and central bank guidance drew broad attention across markets.",
                "Reuters",
                "https://example.com/strong-but-noisy",
                "2026-03-10T09:30:00Z",
                "2026-03-10T09:35:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent mildMarket = newsEvent(
                "mild-market",
                "Corporate bond market update",
                "Traders discussed routine financing conditions.",
                "Reuters",
                "https://example.com/mild-market",
                "2026-03-10T09:20:00Z",
                "2026-03-10T09:25:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(mildMarket, strongButNoisy));

        List<NewsListItemDto> orderedItems = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(orderedItems).extracting(NewsListItemDto::id)
                .containsExactly("strong-but-noisy", "mild-market");
        assertThat(orderedItems.get(0).priorityScore()).isGreaterThan(orderedItems.get(1).priorityScore());
    }

    @Test
    @DisplayName("Published sort should remain driven by recency instead of priority score")
    void getRecentNews_publishedSortRemainsUnchanged() {
        NewsEvent olderHighPriority = newsEvent(
                "older-high-priority",
                "Fed rate decision shakes Treasury market",
                "CPI and yields moved sharply after the FOMC update.",
                "Reuters",
                "https://example.com/older-high-priority",
                "2026-03-10T08:00:00Z",
                "2026-03-10T08:05:00Z",
                NewsStatus.INGESTED,
                null);
        NewsEvent newerGeneric = newsEvent(
                "newer-generic",
                "Company management comments on outlook",
                "A routine corporate update was released.",
                "Reuters",
                "https://example.com/newer-generic",
                "2026-03-10T11:00:00Z",
                "2026-03-10T11:05:00Z",
                NewsStatus.INGESTED,
                null);

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(newerGeneric, olderHighPriority));

        List<String> orderedIds = newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_DESC).stream()
                .map(NewsListItemDto::id)
                .toList();

        assertThat(orderedIds).containsExactly("newer-generic", "older-high-priority");
    }

    @Test
    @DisplayName("Priority score should handle null and empty text safely")
    void getRecentNews_handlesNullAndEmptyTextSafely() {
        NewsEvent sparse = new NewsEvent(
                "sparse",
                null,
                null,
                "",
                null,
                "https://example.com/sparse",
                Instant.parse("2026-03-10T09:00:00Z"),
                Instant.parse("2026-03-10T09:05:00Z"),
                NewsStatus.INGESTED,
                null
        );

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(sparse));

        List<NewsListItemDto> items = newsQueryService.getRecentNews(null, NewsListSort.PRIORITY);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("sparse");
            assertThat(item.priorityScore()).isZero();
        });
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
    @DisplayName("Recent news list should use primary freshness window before fallback window")
    void getRecentNews_prefersPrimaryFreshnessWindowOverFallback() {
        ReflectionTestUtils.setField(newsQueryService, "globalMaxAgeHours", 24L);
        ReflectionTestUtils.setField(newsQueryService, "globalFallbackMaxAgeHours", 36L);

        NewsEvent staleForPrimaryWindow = new NewsEvent(
                "stale-primary-window",
                null,
                "Article should be excluded once the primary window expires",
                "Summary",
                "Reuters",
                "https://example.com/stale-primary-window",
                FIXED_NOW.minus(Duration.ofDays(2)),
                FIXED_NOW.minus(Duration.ofHours(30)),
                NewsStatus.INGESTED,
                null
        );

        given(newsEventRepository.findTop20ByOrderByIngestedAtDesc())
                .willReturn(List.of(staleForPrimaryWindow));

        List<NewsListItemDto> items = newsQueryService.getRecentNews();

        assertThat(items).isEmpty();
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
