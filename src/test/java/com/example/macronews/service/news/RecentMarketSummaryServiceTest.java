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
import com.example.macronews.repository.NewsEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecentMarketSummaryServiceTest {

    @Mock
    private NewsEventRepository newsEventRepository;

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
    }

    @Test
    @DisplayName("enough recent analyzed items should produce a featured market summary")
    void getCurrentSummary_returnsAggregationWhenEnoughRecentItemsExist() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                                List.of(new MarketImpact(MarketType.KOSPI, ImpactDirection.UP, 0.6d))),
                        analyzedNews("news-2", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.OIL, ImpactDirection.DOWN, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().sourceCount()).isEqualTo(3);
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.POSITIVE);
        assertThat(result.get().headlineEn()).isEqualTo("Recent macro signals lean positive");
        assertThat(result.get().keyDrivers()).containsExactly("USD", "KOSPI", "Oil");
        assertThat(result.get().aiSynthesized()).isFalse();
    }

    @Test
    @DisplayName("insufficient recent analyzed items should keep aggregation unavailable")
    void getCurrentSummary_returnsEmptyWhenRecentItemsAreInsufficient() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-17T02:10:00Z",
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
                        analyzedNews("news-1", "2026-03-17T02:30:00Z",
                                List.of(new MacroImpact(MacroVariable.VOLATILITY, ImpactDirection.UP, 0.8d)),
                                List.of()),
                        analyzedNews("news-2", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.DOWN, 0.7d)),
                                List.of()),
                        analyzedNews("news-3", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().dominantSentiment()).isEqualTo(SignalSentiment.NEGATIVE);
        assertThat(result.get().headlineEn()).isEqualTo("Recent macro signals lean defensive");
    }

    @Test
    @DisplayName("key drivers should favor the most frequent macro and market impacts")
    void getCurrentSummary_extractsTopDrivers() {
        given(newsEventRepository.findByStatus(NewsStatus.ANALYZED))
                .willReturn(List.of(
                        analyzedNews("news-1", "2026-03-17T02:30:00Z",
                                List.of(
                                        new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.8d),
                                        new MacroImpact(MacroVariable.OIL, ImpactDirection.UP, 0.5d)
                                ),
                                List.of(new MarketImpact(MarketType.TECH_SECTOR, ImpactDirection.DOWN, 0.6d))),
                        analyzedNews("news-2", "2026-03-17T02:10:00Z",
                                List.of(new MacroImpact(MacroVariable.USD, ImpactDirection.DOWN, 0.7d)),
                                List.of(new MarketImpact(MarketType.TECH_SECTOR, ImpactDirection.UP, 0.4d))),
                        analyzedNews("news-3", "2026-03-17T01:55:00Z",
                                List.of(new MacroImpact(MacroVariable.KOSPI, ImpactDirection.UP, 0.7d)),
                                List.of())
                ));

        var result = recentMarketSummaryService.getCurrentSummary();

        assertThat(result).isPresent();
        assertThat(result.get().keyDrivers()).containsExactly("Tech Sector", "USD", "KOSPI");
    }

    private NewsEvent analyzedNews(String id, String publishedAt, List<MacroImpact> macroImpacts, List<MarketImpact> marketImpacts) {
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
                        Instant.parse("2026-03-17T00:00:00Z"),
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
