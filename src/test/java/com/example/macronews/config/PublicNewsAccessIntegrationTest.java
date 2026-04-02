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
import com.example.macronews.service.news.AiMarketSummaryService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
import com.example.macronews.service.news.MarketSummarySnapshotService;
import com.example.macronews.service.news.RecentMarketSummaryService;
import java.util.List;
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

    @BeforeEach
    void setUp() {
        given(newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_DESC)).willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(newsQueryService.getNewsDetail("non-existent-id")).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
        given(marketSummarySnapshotService.getLatestValidSummary()).willReturn(Optional.empty());
        given(aiMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
        given(recentMarketSummaryService.getCurrentSummary()).willReturn(Optional.empty());
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
}
