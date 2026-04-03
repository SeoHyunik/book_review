package com.example.macronews.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.macronews.dto.FeaturedMarketSummaryDto;
import com.example.macronews.dto.MarketSummaryDetailDto;
import com.example.macronews.dto.MarketSummarySupportingNewsDto;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.repository.UserRepository;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
        given(marketDataFacade.getUs10y()).willReturn(Optional.empty());
        given(marketDataFacade.getOil()).willReturn(Optional.empty());
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
    void givenAnonymousUser_whenRequestNewsListWithoutLocale_thenDefaultToKorean() throws Exception {
        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("news/list"))
                .andExpect(model().attribute("currentLang", "ko"))
                .andExpect(content().string(containsString("lang=\"ko\"")))
                .andExpect(content().string(containsString("시장을 흔드는 핵심 뉴스를 빠르게 읽고, AI 해석으로 방향을 파악하세요.")));
    }

    @Test
    void givenAnonymousUser_whenRequestNewsListWithEnglishLocale_thenRenderEnglish() throws Exception {
        mockMvc.perform(get("/news").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("news/list"))
                .andExpect(model().attribute("currentLang", "en"))
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("Read the macro headlines moving the market, with AI-guided interpretation.")));
    }

    @Test
    void givenAnonymousUser_whenRequestNewsListWithDetailedSource_thenRenderCombinedSourceLabel() throws Exception {
        given(newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(newsListItem("NAVER", "NAVER-경향")));

        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("news/list"))
                .andExpect(content().string(containsString("NAVER-경향")));
    }

    @Test
    void givenAnonymousUser_whenRequestNewsListWithMissingSourceDetail_thenFallbackToCoarseSource() throws Exception {
        given(newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(newsListItem("NAVER", "NAVER")));

        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("news/list"))
                .andExpect(content().string(containsString("NAVER")));
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
    void givenAnonymousUser_whenRequestMarketSummaryCurrent_thenReturnOk() throws Exception {
        String newsId = "news-1";
        given(aiMarketSummaryService.getCurrentSummary())
                .willReturn(Optional.of(currentAiMarketSummary(newsId)));
        given(newsQueryService.getNewsItemsByIds(List.of(newsId)))
                .willReturn(List.of(marketSummaryNewsItem(newsId)));

        mockMvc.perform(get("/market-summary/current"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("market-summary/detail"))
                .andExpect(model().attribute("marketSummarySourceMode", "ai"))
                .andExpect(model().attribute("isCurrentSummary", true))
                .andExpect(model().attribute("isStoredSnapshot", false));
    }

    @Test
    void givenAnonymousUser_whenRequestMarketSummaryDetail_thenReturnOk() throws Exception {
        String snapshotId = "507f1f77bcf86cd799439011";
        given(marketSummarySnapshotService.getSnapshotDetail(snapshotId))
                .willReturn(Optional.of(marketSummaryDetail(snapshotId)));

        mockMvc.perform(get("/market-summary/" + snapshotId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("market-summary/detail"))
                .andExpect(model().attribute("marketSummarySourceMode", "stored"))
                .andExpect(model().attribute("isCurrentSummary", false))
                .andExpect(model().attribute("isStoredSnapshot", true));
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
    void givenRatesTopic_whenRequest_thenReturnOk() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(ratesNewsItem()));
        given(marketDataFacade.getUs10y())
                .willReturn(Optional.of(new Us10ySnapshotDto(
                        4.21d,
                        LocalDate.parse("2026-03-17"),
                        "FRED",
                        "DGS10"
                )));
        given(marketForecastQueryService.getCurrentSnapshot())
                .willReturn(Optional.of(new MarketForecastSnapshotDto(
                        MarketMood.CLOUD,
                        "Rates stay elevated",
                        "Rates stay elevated",
                        "Bond yields remain the key macro anchor.",
                        "Bond yields remain the key macro anchor.",
                        List.of("Rates"),
                        List.of("topic-2"),
                        List.of("Treasury yields climb after rate expectations shift"),
                        Map.of(),
                        Instant.parse("2026-03-17T03:00:00Z").toString(),
                        1
                )));

        mockMvc.perform(get("/topic/rates"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/rates"))
                .andExpect(model().attribute("ratesNewsItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("pageTitleKey", "page.topic.rates.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.topic.rates.description"));
    }

    @Test
    void givenNoRatesData_whenRequest_thenRenderEmpty() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getUs10y()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/rates"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/rates"))
                .andExpect(model().attribute("ratesNewsItems", List.of()))
                .andExpect(model().attribute("us10ySnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void givenOilTopic_whenRequest_thenReturnOk() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(oilNewsItem()));
        given(marketDataFacade.getOil())
                .willReturn(Optional.of(new OilSnapshotDto(
                        82.4d,
                        86.7d,
                        Instant.parse("2026-03-17T03:00:00Z")
                )));
        given(marketForecastQueryService.getCurrentSnapshot())
                .willReturn(Optional.of(new MarketForecastSnapshotDto(
                        MarketMood.CLOUD,
                        "Oil market remains firm",
                        "Oil market remains firm",
                        "Energy pricing continues to anchor macro attention.",
                        "Energy pricing continues to anchor macro attention.",
                        List.of("Oil"),
                        List.of("topic-3"),
                        List.of("Oil climbs as crude supply tightens"),
                        Map.of(),
                        Instant.parse("2026-03-17T03:00:00Z").toString(),
                        1
                )));

        mockMvc.perform(get("/topic/oil"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/oil"))
                .andExpect(model().attribute("oilNewsItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("pageTitleKey", "page.topic.oil.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.topic.oil.description"));
    }

    @Test
    void givenNoOilData_whenRequest_thenRenderEmpty() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getOil()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/oil"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("topic/oil"))
                .andExpect(model().attribute("oilNewsItems", List.of()))
                .andExpect(model().attribute("oilSnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void givenAnonymousUser_whenRequestArchive_thenReturnPage() throws Exception {
        given(newsQueryService.getArchiveNews(1, 20))
                .willReturn(new PageImpl<>(List.of(archiveNewsItem()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("archive/list"))
                .andExpect(model().attribute("archiveItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("archiveCurrentPage", 1))
                .andExpect(model().attribute("archiveTotalPages", 1))
                .andExpect(model().attribute("pageTitleKey", "page.archive.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.archive.description"));
    }

    @Test
    void givenAnonymousUser_whenRequestArchiveWithPage_thenReturnRequestedPage() throws Exception {
        given(newsQueryService.getArchiveNews(2, 20))
                .willReturn(new PageImpl<>(List.of(archiveNewsItem()), PageRequest.of(1, 20), 21));

        mockMvc.perform(get("/archive?page=2"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("archive/list"))
                .andExpect(model().attribute("archiveItems", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("archiveCurrentPage", 2))
                .andExpect(model().attribute("archiveTotalPages", 2));
    }

    @Test
    void givenAnonymousUser_whenRequestArchiveWithNoNews_thenRenderEmpty() throws Exception {
        given(newsQueryService.getArchiveNews(1, 20))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/archive"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("archive/list"))
                .andExpect(model().attribute("archiveItems", List.of()))
                .andExpect(model().attribute("archiveCount", 0L))
                .andExpect(model().attribute("archiveCurrentPage", 1))
                .andExpect(model().attribute("archiveTotalPages", 0));
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

    private NewsListItemDto ratesNewsItem() {
        return new NewsListItemDto(
                "topic-2",
                "Treasury yields climb after rate expectations shift",
                "Treasury yields climb after rate expectations shift",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.NEGATIVE,
                "Rates UP",
                "Bond market pricing turns more cautious.",
                11
        );
    }

    private NewsListItemDto oilNewsItem() {
        return new NewsListItemDto(
                "topic-3",
                "Oil climbs as crude supply tightens",
                "Oil climbs as crude supply tightens",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "Oil UP",
                "Crude and energy pricing firm as supply tightens.",
                10
        );
    }

    private MarketSummaryDetailDto marketSummaryDetail(String id) {
        return new MarketSummaryDetailDto(
                id,
                Instant.parse("2026-03-17T03:00:00Z"),
                3,
                3,
                "headline ko",
                "headline en",
                "summary ko",
                "summary en",
                "view ko",
                "view en",
                SignalSentiment.POSITIVE,
                0.8d,
                List.of("USD", "Oil"),
                List.of(new MarketSummarySupportingNewsDto(
                        "news-1",
                        "KOSPI rises",
                        "Yonhap",
                        Instant.parse("2026-03-17T02:10:00Z"),
                        ImpactDirection.UP,
                        SignalSentiment.POSITIVE
                ))
        );
    }

    private FeaturedMarketSummaryDto currentAiMarketSummary(String newsId) {
        return new FeaturedMarketSummaryDto(
                "AI headline ko",
                "AI headline en",
                "AI summary ko",
                "AI summary en",
                Instant.parse("2026-03-17T03:00:00Z"),
                1,
                3,
                Instant.parse("2026-03-17T00:00:00Z"),
                Instant.parse("2026-03-17T03:00:00Z"),
                SignalSentiment.POSITIVE,
                List.of("USD"),
                List.of(newsId),
                "market view ko",
                "market view en",
                0.8d,
                true,
                null
        );
    }

    private NewsListItemDto marketSummaryNewsItem(String id) {
        return new NewsListItemDto(
                id,
                "KOSPI rises",
                "KOSPI rises",
                "Yonhap",
                Instant.parse("2026-03-17T02:10:00Z"),
                Instant.parse("2026-03-17T02:15:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "KOSPI UP",
                "Market breadth improves.",
                9
        );
    }

    private NewsListItemDto newsListItem(String source, String displaySource) {
        return new NewsListItemDto(
                "news-1",
                "KOSPI rises",
                "KOSPI rises",
                source,
                displaySource,
                Instant.parse("2026-03-17T02:10:00Z"),
                Instant.parse("2026-03-17T02:15:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "KOSPI UP",
                "Market breadth improves.",
                9
        );
    }
}
