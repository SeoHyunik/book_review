package com.example.macronews.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.dto.MarketSignalOverviewDto;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.service.auth.AnonymousDetailViewGateService;
import com.example.macronews.service.forecast.MarketForecastQueryService;
import com.example.macronews.service.news.NewsQueryService;
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
    private AnonymousDetailViewGateService anonymousDetailViewGateService;

    @InjectMocks
    private NewsController newsController;

    @Test
    @DisplayName("list should expose aggregated market forecast snapshot when present")
    void list_addsAggregatedSnapshotToModel() {
        MarketForecastSnapshotDto snapshot = new MarketForecastSnapshotDto(
                MarketMood.CLOUD,
                "한국어 헤드라인",
                "English headline",
                "한국어 요약",
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
        given(marketForecastQueryService.getCurrentSnapshot()).willReturn(Optional.of(snapshot));

        ConcurrentModel model = new ConcurrentModel();
        String viewName = newsController.list(null, null, model);

        assertThat(viewName).isEqualTo("news/list");
        assertThat(model.getAttribute("marketForecastSnapshot")).isEqualTo(snapshot);
    }
}
