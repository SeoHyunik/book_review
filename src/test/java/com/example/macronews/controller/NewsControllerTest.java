package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.NewsDetailDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsListItemDto;
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
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

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
    @DisplayName("detail should expose original article summary when it is distinct from the title")
    void detail_exposesDistinctOriginalArticleSummary() {
        NewsDetailDto detail = new NewsDetailDto(
                "news-1",
                "Fed keeps rates unchanged",
                "Officials signaled a cautious stance while watching inflation data.",
                "Reuters",
                "https://example.com/news-1",
                Instant.parse("2026-03-17T02:30:00Z"),
                NewsStatus.ANALYZED,
                new AnalysisResult(
                        "gpt-4o-mini",
                        Instant.parse("2026-03-17T02:40:00Z"),
                        "?곗? ?숆껐",
                        "Fed pause",
                        "?쒖옣 諛섏쓳? ?쒗븳?곸씠??",
                        "Market reaction remains contained.",
                        List.of(),
                        List.of()
                )
        );
        given(newsQueryService.getNewsDetail("news-1")).willReturn(Optional.of(detail));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.detail(
                "news-1",
                authenticatedUser(),
                mock(HttpSession.class),
                model,
                new RedirectAttributesModelMap());

        assertThat(viewName).isEqualTo("news/detail");
        assertThat(model.getAttribute("originalArticleSummary"))
                .isEqualTo("Officials signaled a cautious stance while watching inflation data.");
    }

    @Test
    @DisplayName("detail should not fall back to title when original article summary is blank")
    void detail_doesNotFallbackToTitleWhenOriginalSummaryIsBlank() {
        NewsDetailDto detail = new NewsDetailDto(
                "news-2",
                "Fed keeps rates unchanged",
                "   ",
                "Reuters",
                "https://example.com/news-2",
                Instant.parse("2026-03-17T02:30:00Z"),
                NewsStatus.ANALYZED,
                new AnalysisResult(
                        "gpt-4o-mini",
                        Instant.parse("2026-03-17T02:40:00Z"),
                        "?곗? ?숆껐",
                        "Fed pause",
                        "?쒖옣 諛섏쓳? ?쒗븳?곸씠??",
                        "Market reaction remains contained.",
                        List.of(),
                        List.of()
                )
        );
        given(newsQueryService.getNewsDetail("news-2")).willReturn(Optional.of(detail));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.detail(
                "news-2",
                authenticatedUser(),
                mock(HttpSession.class),
                model,
                new RedirectAttributesModelMap());

        assertThat(viewName).isEqualTo("news/detail");
        assertThat(model.getAttribute("originalArticleSummary")).isEqualTo("");
    }

    @Test
    @DisplayName("detail should not expose title-equal summary as original article summary")
    void detail_doesNotExposeTitleEqualSummary() {
        NewsDetailDto detail = new NewsDetailDto(
                "news-3",
                "Fed keeps rates unchanged",
                "Fed keeps rates unchanged",
                "Reuters",
                "https://example.com/news-3",
                Instant.parse("2026-03-17T02:30:00Z"),
                NewsStatus.INGESTED,
                null
        );
        given(newsQueryService.getNewsDetail("news-3")).willReturn(Optional.of(detail));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.detail(
                "news-3",
                authenticatedUser(),
                mock(HttpSession.class),
                model,
                new RedirectAttributesModelMap());

        assertThat(viewName).isEqualTo("news/detail");
        assertThat(model.getAttribute("originalArticleSummary")).isEqualTo("");
    }

    @Test
    @DisplayName("detail should redirect to the news list when detail lookup fails")
    void givenDetailLookupFailure_whenDetail_thenRedirectToNewsList() {
        willThrow(new RuntimeException("detail unavailable"))
                .given(newsQueryService).getNewsDetail("news-error");

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String viewName = newsController.detail(
                "news-error",
                null,
                mock(HttpSession.class),
                new ConcurrentModel(),
                redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/news");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
    }

    @Test
    @DisplayName("detail should redirect to the news list when anonymous detail gating fails")
    void givenAnonymousDetailGateFailure_whenDetail_thenRedirectToNewsList() {
        NewsDetailDto detail = new NewsDetailDto(
                "news-4",
                "Fed keeps rates unchanged",
                "Officials signaled a cautious stance while watching inflation data.",
                "Reuters",
                "https://example.com/news-4",
                Instant.parse("2026-03-17T02:30:00Z"),
                NewsStatus.ANALYZED,
                null
        );
        HttpSession session = mock(HttpSession.class);
        given(newsQueryService.getNewsDetail("news-4")).willReturn(Optional.of(detail));
        willThrow(new RuntimeException("gate unavailable"))
                .given(anonymousDetailViewGateService)
                .canAccess("news-4", session);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = newsController.detail(
                "news-4",
                null,
                session,
                new ConcurrentModel(),
                redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/news");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
    }

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
        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
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
                false,
                null
        );

        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
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
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(true);
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("recent-summary");
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
                true,
                null
        );

        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
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
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(true);
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("ai-summary");
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
                true,
                "snapshot-1"
        );

        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.of(storedSummary));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredStoredMarketSummary")).isEqualTo(storedSummary);
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(true);
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("stored-summary");
    }

    @Test
    @DisplayName("list should fall back to article mode only when no market summary is available")
    void list_fallsBackToArticleModeLast() {
        NewsListItemDto featuredNews = new NewsListItemDto(
                "news-1",
                "Raw market headline",
                "Raw market headline",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                com.example.macronews.domain.NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "Headline",
                "Interpretation",
                10
        );

        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(featuredNews));
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredNews")).isEqualTo(featuredNews);
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(false);
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("article");
    }

    @Test
    @DisplayName("list should fail open when optional featured summary lookup throws")
    void list_failsOpenWhenFeaturedSummaryLookupThrows() {
        NewsListItemDto featuredNews = new NewsListItemDto(
                "news-1",
                "Raw market headline",
                "Raw market headline",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                com.example.macronews.domain.NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "Headline",
                "Interpretation",
                10
        );

        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(featuredNews));
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
        willThrow(new RuntimeException("snapshot store unavailable"))
                .given(marketSummarySnapshotService).getLatestValidSummary();

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("featuredNews")).isEqualTo(featuredNews);
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(false);
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("article");
        assertThat(model.getAttribute("featuredStoredMarketSummary")).isNull();
        assertThat(model.getAttribute("featuredAiMarketSummary")).isNull();
        assertThat(model.getAttribute("featuredMarketSummary")).isNull();
    }

    @Test
    @DisplayName("list should fail open when recent news lookup throws")
    void givenRecentNewsFailure_whenList_thenRenderNewsPageWithEmptyItems() {
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        willThrow(new RuntimeException("recent news unavailable"))
                .given(newsQueryService).getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("newsItems")).isEqualTo(List.of());
        assertThat(model.getAttribute("featuredNews")).isNull();
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("article");
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(false);
    }

    @Test
    @DisplayName("list should fail open when market signal overview lookup throws")
    void givenMarketSignalOverviewFailure_whenList_thenRenderNewsPageWithEmptyOverview() {
        NewsListItemDto newsItem = new NewsListItemDto(
                "news-4",
                "Market headline",
                "Market headline",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "Headline",
                "Interpretation",
                10
        );
        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(newsItem));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        willThrow(new RuntimeException("market signal unavailable"))
                .given(newsQueryService).getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC);

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("newsItems")).isEqualTo(List.of(newsItem));
        assertThat(model.getAttribute("marketSignalOverview")).isEqualTo(new MarketSignalOverviewDto(List.of()));
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("article");
        assertThat(model.getAttribute("featuredSummaryMode")).isEqualTo(false);
    }

    @Test
    @DisplayName("list should fail open when market forecast lookup throws")
    void list_failsOpenWhenMarketForecastLookupThrows() {
        given(newsQueryService.getRecentNewsForToday(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, com.example.macronews.service.news.NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        willThrow(new RuntimeException("forecast unavailable"))
                .given(marketForecastQueryService).getCurrentSnapshot();

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("marketForecastSnapshot")).isNull();
        assertThat(model.getAttribute("featuredPrimaryMode")).isEqualTo("article");
    }

    private Authentication authenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        given(authentication.isAuthenticated()).willReturn(true);
        return authentication;
    }
}
