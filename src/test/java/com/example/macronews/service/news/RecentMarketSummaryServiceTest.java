package com.example.macronews.service.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.example.macronews.domain.NewsEvent;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.dto.forecast.MarketForecastSummaryHandoffDto;
import com.example.macronews.repository.NewsEventRepository;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RecentMarketSummaryServiceTest {

    @Mock
    private NewsEventRepository newsEventRepository;

    @Mock
    private MarketForecastQueryService marketForecastQueryService;

    @InjectMocks
    private RecentMarketSummaryService recentMarketSummaryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recentMarketSummaryService, "clock",
                Clock.fixed(Instant.parse("2026-03-17T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        ReflectionTestUtils.setField(recentMarketSummaryService, "enabled", true);
        ReflectionTestUtils.setField(recentMarketSummaryService, "windowHours", 3);
        ReflectionTestUtils.setField(recentMarketSummaryService, "maxItems", 10);
        ReflectionTestUtils.setField(recentMarketSummaryService, "minItems", 3);
        lenient().when(marketForecastQueryService.getCurrentSummaryHandoff()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("enough recent analyzed items should produce a featured market summary")
    void getCurrentSummary_returnsAggregationWhenEnoughRecentItemsExist() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                                List.of(new MarketImpact(MarketType.KOSPI, ImpactDirection.UP, 0.6d))),
                        analyzedNews("news-2", "2026-03-11T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-10T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.DOWN, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().sourceCount()).isEqualTo(3);
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(result.get().headlineEn()).isEqualTo("Recent macro signals lean positive");
        assertThat(result.get().keyDrivers()).containsExactly("USD", "KOSPI", "Oil");
        assertThat(result.get().confidence()).isNotNull();
        assertThat(result.get().aiSynthesized()).isFalse();
    }

    @Test
    @DisplayName("insufficient recent analyzed items should keep aggregation unavailable")
    void getCurrentSummary_returnsEmptyWhenRecentItemsAreInsufficient() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-10T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-10T02:10:00Z", "2026-03-16T20:10:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.7d)),
                                List.of())
                ));

        assertThat(recentMarketSummaryService.getCurrentSummary()).isEmpty();
    }

    @Test
    @DisplayName("dominant sentiment should resolve by article-level semantic majority")
    void getCurrentSummary_resolvesDominantSentimentDeterministically() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.VOLATILITY, ImpactDirection.UP, 0.8d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.DOWN, 0.7d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(result.get().headlineEn()).isEqualTo("Recent macro signals lean defensive");
        assertThat(result.get().confidence()).isNotNull();
    }

    @Test
    @DisplayName("key drivers should favor the most frequent macro and market impacts")
    void getCurrentSummary_extractsTopDrivers() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(
                                        new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d),
                                        new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.5d)
                                ),
                                List.of(new MarketImpact(MarketType.TECH_SECTOR, ImpactDirection.DOWN, 0.6d))),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of(new MarketImpact(MarketType.TECH_SECTOR, ImpactDirection.UP, 0.4d))),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().keyDrivers()).containsExactly("Tech Sector", "USD", "KOSPI");
    }

    @Test
    @DisplayName("recent summary should use analysis completion timing even when publishedAt is old")
    void loadRecentAnalyzedNews_usesAnalysisCreatedAtBasis() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-01T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-02T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.7d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-03T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.7d)),
                                List.of())
                ));

        List<NewsEvent> recentItems = recentMarketSummaryService.loadRecentAnalyzedNews();

        assertThat(recentItems).hasSize(3);
    }

    @Test
    @DisplayName("recent summary should surface directional sentiment when strong negative signals outweigh neutral noise")
    void getCurrentSummary_reducesNeutralCompressionForDirectionalSignals() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.92d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.86d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.NEUTRAL, 0.34d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(result.get().confidence()).isGreaterThan(0.6d);
    }

    @Test
    @DisplayName("recent summary should remain neutral when opposing directional weights are near tied")
    void getCurrentSummary_keepsNeutralForNearTie() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.62d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.58d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.GOLD, ImpactDirection.UP, 0.52d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.NEUTRAL);
        assertThat(result.get().confidence()).isNotNull();
    }

    @Test
    @DisplayName("price-aware confidence modifier should stay secondary and fail open when forecast data is missing")
    void getCurrentSummary_keepsConfidenceWhenForecastDataMissing() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.82d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.76d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.40d)),
                                List.of())
                ));
        given(marketForecastQueryService.getCurrentSummaryHandoff())
                .willThrow(new RuntimeException("forecast unavailable"));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(result.get().confidence()).isNotNull();
    }

    @Test
    @DisplayName("price-aware confidence modifier may slightly increase confidence without flipping direction")
    void getCurrentSummary_appliesSmallPriceAwareConfidenceModifier() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-12T02:30:00Z", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.82d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-12T02:10:00Z", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.76d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-12T01:55:00Z", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.40d)),
                                List.of())
                ));
        given(marketForecastQueryService.getCurrentSummaryHandoff())
                .willReturn(Optional.of(new MarketForecastSummaryHandoffDto(
                        MarketMood.SUN,
                        Map.of(
                                MacroVariable.USD, ImpactDirection.DOWN,
                                MacroVariable.KOSPI, ImpactDirection.UP
                        ),
                        List.of("USD", "KOSPI"),
                        List.of("news-1"),
                        3,
                        "2026-03-17T03:00:00Z"
                )));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(result.get().confidence()).isGreaterThan(0.6d);
        assertThat(result.get().confidence()).isLessThanOrEqualTo(0.96d);
    }

    private NewsEvent analyzedNews(String id, String publishedAt, String analyzedAt,
            List<MacroImpact> macroImpacts, List<MarketImpact> marketImpacts) {
        return new NewsEvent(
                id,
                null,
                "Title " + id,
                "Summary " + id,
                "Source",
                "https://example.com/" + id,
                Instant.parse(publishedAt),
                Instant.parse(publishedAt),
                NewsStatus.ANALYZED,
                new AnalysisResult(
                        "test-model",
                        Instant.parse(analyzedAt),
                        null,
                        null,
                        null,
                        null,
                        macroImpacts,
                        marketImpacts
                )
        );
    }
}
