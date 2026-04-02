package com.example.macronews.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.repository.UserRepository;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NewsQueryService newsQueryService;

    @MockitoBean
    private MarketDataFacade marketDataFacade;

    @MockitoBean
    private MarketForecastQueryService marketForecastQueryService;

    @MockitoBean
    private MarketSummarySnapshotService marketSummarySnapshotService;

    @MockitoBean
    private AiMarketSummaryService aiMarketSummaryService;

    @MockitoBean
    private RecentMarketSummaryService recentMarketSummaryService;

    @BeforeEach
    void setUp() {
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(marketDataFacade.getUs10y()).willReturn(Optional.empty());
        given(marketDataFacade.getOil()).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("givenTopicRequest_whenDollar_thenReturnPage")
    void givenTopicRequest_whenDollar_thenReturnPage() throws Exception {
        NewsListItemDto related = new NewsListItemDto(
                "news-1",
                "Dollar strength lifts the US dollar index",
                "Dollar strength lifts the US dollar index",
                "Reuters",
                Instant.parse("2026-03-17T02:30:00Z"),
                Instant.parse("2026-03-17T02:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.UP,
                SignalSentiment.POSITIVE,
                "USD UP",
                "Dollar strength remains supported by higher yields.",
                9
        );
        NewsListItemDto unrelated = new NewsListItemDto(
                "news-2",
                "Company earnings surprise",
                "Company earnings surprise",
                "Reuters",
                Instant.parse("2026-03-17T01:30:00Z"),
                Instant.parse("2026-03-17T01:35:00Z"),
                NewsStatus.ANALYZED,
                true,
                true,
                ImpactDirection.NEUTRAL,
                SignalSentiment.NEUTRAL,
                "Equity",
                "Earnings summary",
                1
        );
        DxySnapshotDto dxySnapshot = new DxySnapshotDto(
                104.2d,
                Instant.parse("2026-03-17T03:00:00Z"),
                "TWELVE_DATA_DIRECT",
                "DXY",
                false
        );
        MarketForecastSnapshotDto forecastSnapshot = new MarketForecastSnapshotDto(
                MarketMood.CLOUD,
                "Dollar tone stays defensive",
                "Dollar tone stays defensive",
                "Recent headlines still point to a firm dollar backdrop.",
                "Recent headlines still point to a firm dollar backdrop.",
                List.of("USD", "Yield"),
                List.of("news-1"),
                List.of("Dollar strength lifts the US dollar index"),
                Map.of(),
                Instant.parse("2026-03-17T03:00:00Z").toString(),
                1
        );
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(related, unrelated));
        given(marketDataFacade.getDxy()).willReturn(Optional.of(dxySnapshot));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.of(forecastSnapshot));

        mockMvc.perform(get("/topic/dollar"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/dollar"))
                .andExpect(model().attribute("dollarNewsItems", List.of(related)))
                .andExpect(model().attribute("dxySnapshot", dxySnapshot))
                .andExpect(model().attribute("forecastSnapshot", forecastSnapshot));
    }

    @Test
    @DisplayName("givenNoMarketData_whenTopic_thenStillRender")
    void givenNoMarketData_whenTopic_thenStillRender() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getDxy()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/dollar"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/dollar"))
                .andExpect(model().attribute("dollarNewsItems", List.of()))
                .andExpect(model().attribute("dxySnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("givenTopicRequest_whenRates_thenReturnPage")
    void givenTopicRequest_whenRates_thenReturnPage() throws Exception {
        NewsListItemDto related = new NewsListItemDto(
                "news-3",
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
        Us10ySnapshotDto us10ySnapshot = new Us10ySnapshotDto(
                4.21d,
                java.time.LocalDate.parse("2026-03-17"),
                "FRED",
                "DGS10"
        );
        MarketForecastSnapshotDto forecastSnapshot = new MarketForecastSnapshotDto(
                MarketMood.CLOUD,
                "Rates remain elevated",
                "Rates remain elevated",
                "Bond yields are still anchoring the macro backdrop.",
                "Bond yields are still anchoring the macro backdrop.",
                List.of("Rates", "Treasury"),
                List.of("news-3"),
                List.of("Treasury yields climb after rate expectations shift"),
                Map.of(),
                Instant.parse("2026-03-17T03:00:00Z").toString(),
                1
        );
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(related));
        given(marketDataFacade.getUs10y()).willReturn(Optional.of(us10ySnapshot));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.of(forecastSnapshot));

        mockMvc.perform(get("/topic/rates"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/rates"))
                .andExpect(model().attribute("ratesNewsItems", List.of(related)))
                .andExpect(model().attribute("us10ySnapshot", us10ySnapshot))
                .andExpect(model().attribute("forecastSnapshot", forecastSnapshot))
                .andExpect(model().attribute("pageTitleKey", "page.topic.rates.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.topic.rates.description"));
    }

    @Test
    @DisplayName("givenNoRatesData_whenRates_thenStillRender")
    void givenNoRatesData_whenRates_thenStillRender() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getUs10y()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/rates"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/rates"))
                .andExpect(model().attribute("ratesNewsItems", List.of()))
                .andExpect(model().attribute("us10ySnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("givenTopicRequest_whenOil_thenReturnPage")
    void givenTopicRequest_whenOil_thenReturnPage() throws Exception {
        NewsListItemDto related = new NewsListItemDto(
                "news-4",
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
        OilSnapshotDto oilSnapshot = new OilSnapshotDto(
                82.4d,
                86.7d,
                Instant.parse("2026-03-17T03:00:00Z")
        );
        MarketForecastSnapshotDto forecastSnapshot = new MarketForecastSnapshotDto(
                MarketMood.CLOUD,
                "Oil market remains firm",
                "Oil market remains firm",
                "Energy pricing continues to anchor macro attention.",
                "Energy pricing continues to anchor macro attention.",
                List.of("Oil"),
                List.of("news-4"),
                List.of("Oil climbs as crude supply tightens"),
                Map.of(),
                Instant.parse("2026-03-17T03:00:00Z").toString(),
                1
        );
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of(related));
        given(marketDataFacade.getOil()).willReturn(Optional.of(oilSnapshot));
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.of(forecastSnapshot));

        mockMvc.perform(get("/topic/oil"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/oil"))
                .andExpect(model().attribute("oilNewsItems", List.of(related)))
                .andExpect(model().attribute("oilSnapshot", oilSnapshot))
                .andExpect(model().attribute("forecastSnapshot", forecastSnapshot))
                .andExpect(model().attribute("pageTitleKey", "page.topic.oil.title"))
                .andExpect(model().attribute("pageDescriptionKey", "page.topic.oil.description"));
    }

    @Test
    @DisplayName("givenNoOilData_whenOil_thenStillRender")
    void givenNoOilData_whenOil_thenStillRender() throws Exception {
        given(newsQueryService.getRecentNews(NewsStatus.ANALYZED, NewsListSort.PUBLISHED_DESC))
                .willReturn(List.of());
        given(marketDataFacade.getOil()).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());

        mockMvc.perform(get("/topic/oil"))
                .andExpect(status().isOk())
                .andExpect(view().name("topic/oil"))
                .andExpect(model().attribute("oilNewsItems", List.of()))
                .andExpect(model().attribute("oilSnapshot", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("forecastSnapshot", org.hamcrest.Matchers.nullValue()));
    }
}
