package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.service.auth.AnonymousDetailViewGateService;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsQueryService newsQueryService;

    @Mock
    private MarketForecastQueryService marketForecastQueryService;

    @Mock
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @Mock
    private AiMarketSummaryService aiMarketSummaryService;

    @Mock
    private RecentMarketSummaryService recentMarketSummaryService;

    @Mock
    private AnonymousDetailViewGateService anonymousDetailViewGateService;

    @InjectMocks
    private NewsController newsController;

    @Test
    @DisplayName("list should expose aggregated market forecast snapshot when present")
    void list_addsAggregatedSnapshotToModel() {
        MarketForecastSnapshotDto snapshot = new MarketForecastSnapshotDto(
                MarketMood.CLOUD,
                "Korean headline",
                "English headline",
                "Korean summary",
                "English summary",
                List.of("driver-1"),
                List.of("news-1"),
                List.of("KOSPI rises"),
                Map.of(MacroVariable.KOSPI, ImpactDirection.UP),
                Instant.parse("2026-03-13T00:00:00Z").toString(),
                2
        );
        given(newsQueryService.getRecentNews(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.of(snapshot));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("marketForecastSnapshot")).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("list should prefer recent market summary when aggregation is available")
    void list_addsRecentMarketSummaryToModel() {
        FeaturedMarketSummaryDto summary = new FeaturedMarketSummaryDto(
                "Recent macro signals lean positive (ko)",
                "Recent macro signals lean positive",
                "Recent analyzed headlines over the last 3 hours (ko)",
                "Built from 3 analyzed headlines over the last 3 hours, with USD / Oil appearing most often.",
                Instant.parse("2026-03-17T03:00:00Z"),
                3,
                3,
                Instant.parse("2026-03-17T00:30:00Z"),
                Instant.parse("2026-03-17T02:30:00Z"),
                SignalSentiment.POSITIVE,
                List.of("USD", "Oil"),
                List.of("news-3", "news-2", "news-1"),
                null,
                null,
                null,
                false
        );

        given(newsQueryService.getRecentNews(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.of(summary));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredMarketSummary")).isEqualTo(summary);
        assertThat(model.getAttribute("marketForecastSnapshot")).isNull();
    }

    @Test
    @DisplayName("list should prefer synthesized market summary when available")
    void list_addsAiMarketSummaryToModel() {
        FeaturedMarketSummaryDto aiSummary = new FeaturedMarketSummaryDto(
                "AI market summary (ko)",
                "AI market summary",
                "AI summary body (ko)",
                "AI summary body",
                Instant.parse("2026-03-17T03:00:00Z"),
                4,
                3,
                Instant.parse("2026-03-17T00:30:00Z"),
                Instant.parse("2026-03-17T02:30:00Z"),
                SignalSentiment.NEGATIVE,
                List.of("USD", "Volatility"),
                List.of("news-4", "news-3"),
                "defensive view ko",
                "defensive view en",
                0.78d,
                true
        );

        given(newsQueryService.getRecentNews(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.of(aiSummary));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredAiMarketSummary")).isEqualTo(aiSummary);
        assertThat(model.getAttribute("featuredMarketSummary")).isNull();
    }

    @Test
    @DisplayName("list should prefer stored snapshot when available")
    void list_addsStoredMarketSummaryToModel() {
        FeaturedMarketSummaryDto storedSummary = new FeaturedMarketSummaryDto(
                "AI market snapshot ko",
                "AI market snapshot",
                "Stored summary ko",
                "Stored summary en",
                Instant.parse("2026-03-17T03:00:00Z"),
                4,
                3,
                Instant.parse("2026-03-17T00:30:00Z"),
                Instant.parse("2026-03-17T02:30:00Z"),
                SignalSentiment.NEGATIVE,
                List.of("USD", "Volatility"),
                List.of("news-4", "news-3"),
                "defensive view ko",
                "defensive view en",
                0.78d,
                true
        );

        given(newsQueryService.getRecentNews(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.of(storedSummary));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredStoredMarketSummary")).isEqualTo(storedSummary);
    }
}
