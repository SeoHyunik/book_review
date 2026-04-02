package com.example.macronews.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.repository.UserRepository;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicNewsAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NewsQueryService newsQueryService;

    @MockitoBean
    private MarketForecastQueryService marketForecastQueryService;

    @MockitoBean
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @MockitoBean
    private AiMarketSummaryService aiMarketSummaryService;

    @MockitoBean
    private RecentMarketSummaryService recentMarketSummaryService;

    @MockitoBean
    private MarketDataFacade marketDataFacade;

    @BeforeEach
    void setUp() {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(newsQueryService.getNewsDetail("non-existent-id")).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(marketDataFacade.getDxy()).willReturn(Optional.empty());
    }

    @Test
    void givenRecentNewsFailure_whenRequestNewsList_thenReturnOkAndFallbackModel() throws Exception {
        willThrow(new RuntimeException("recent news unavailable"))
                .given(newsQueryService).getRecentNews(null, NewsListSort.PUBLISHED_DESC);

        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("news/list"))
                .andExpect(model().attribute("newsItems", List.of()))
                .andExpect(model().attribute("marketSignalOverview", new MarketSignalOverviewDto(List.of())))
                .andExpect(model().attribute("marketForecastSnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("featuredPrimaryMode", "article"))
                .andExpect(model().attribute("featuredSummaryMode", false));
    }

    @Test
    void givenNewsList_whenRequest_thenReturnOk() throws Exception {
        mockMvc.perform(get("/news"))
                .andExpect(status().isOk());
    }

    @Test
    void givenMissingNewsDetail_whenRequest_thenRedirectToList() throws Exception {
        mockMvc.perform(get("/news/non-existent-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/news"));
    }

    @Test
    void givenNewsDetailFailure_whenRequest_thenRedirectToList() throws Exception {
        willThrow(new RuntimeException("detail lookup unavailable"))
                .given(newsQueryService).getNewsDetail("failing-id");

        mockMvc.perform(get("/news/failing-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/news"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenAnonymousUser_whenRequestAdminRoute_thenRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/news/manual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void givenTopicRequest_whenDollar_thenReturnPage() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(dollarNewsItem()));
        given(marketDataFacade.getDxy())
                .willReturn(Optional.of(new DxySnapshotDto(
                        103.4d,
                        Instant.parse("2026-03-17T03:00:00Z"),
                        "TWELVE_DATA_SYNTHETIC",
                        "FX_BASKET_6",
                        true
                )));
        given(marketForecastQueryService.getCurrentSnapshot())
                .willReturn(Optional.of(new MarketForecastSnapshotDto(
                        MarketMood.CLOUD,
                        "Dollar pressure is easing",
                        "Dollar pressure is easing",
                        "USD-related signals are stabilizing.",
                        "USD-related signals are stabilizing.",
                        List.of("USD"),
                        List.of("topic-1"),
                        List.of("USD pressure eases"),
                        Map.of(),
                        Instant.parse("2026-03-17T03:00:00Z").toString(),
                        1
                )));

        mockMvc.perform(get("/topic/dollar"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/dollar"))
                .andExpect(model().attribute("dollarNewsItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("dxySnapshot", org.hamcrest.Matchers.notNullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.notNullValue()))
                .andExpect(model().attribute("pageTitleKey", "page.topic.dollar.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.topic.dollar.description"));
    }

    @Test
    void givenNoMarketData_whenTopic_thenStillRender() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getDxy()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/dollar"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/dollar"))
                .andExpect(model().attribute("dollarNewsItems", List.of()))
                .andExpect(model().attribute("dxySnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void givenArchiveRequest_whenCalled_thenReturnPage() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(archiveNewsItem()));

        mockMvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("archive/list"))
                .andExpect(model().attribute("archiveItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("pageTitleKey", "page.archive.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.archive.description"));
    }

    @Test
    void givenNoNews_whenArchive_thenRenderEmpty() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());

        mockMvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("archive/list"))
                .andExpect(model().attribute("archiveItems", List.of()))
                .andExpect(model().attribute("archiveCount", 0));
    }

    private NewsListItemDto dollarNewsItem() {
        return new NewsListItemDto(
                "topic-1",
                "USD eases on softer Treasury yields",
                "USD eases on softer Treasury yields",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.DOWN,
                SignalSentiment.NEGATIVE,
                "USD DOWN",
                "Dollar pressure is easing as yields soften.",
                12
        );
    }

    private NewsListItemDto archiveNewsItem() {
        return new NewsListItemDto(
                "archive-1",
                "Fed keeps rates unchanged",
                "Fed keeps rates unchanged",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.NEUTRAL,
                SignalSentiment.NEUTRAL,
                "Rates unchanged",
                "Policy remains steady.",
                8
        );
    }
}
