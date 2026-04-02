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
}
