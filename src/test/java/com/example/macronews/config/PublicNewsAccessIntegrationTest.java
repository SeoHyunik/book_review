package com.example.macronews.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.repository.UserRepository;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.news.NewsListSort;
import com.example.macronews.service.news.NewsQueryService;
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

    @BeforeEach
    void setUp() {
        given(newsQueryService.getRecentNews(null, NewsListSort.PUBLISHED_DESC)).willReturn(List.of());
        given(newsQueryService.getMarketSignalOverview(null, NewsListSort.PUBLISHED_DESC))
                .willReturn(new MarketSignalOverviewDto(List.of()));
        given(newsQueryService.getNewsDetail("non-existent-id")).willReturn(Optional.empty());
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.empty());
    }

    @Test
    void newsListIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/news"))
                .andExpect(status().isOk());
    }

    @Test
    void newsDetailRouteNoLongerRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/news/non-existent-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/news"));
    }

    @Test
    void adminRouteStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/news/manual"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
