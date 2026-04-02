package com.example.macronews.service.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import com.example.macronews.domain.SignalSentiment;
import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.NewsQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketForecastQueryServiceTest {

    @Mock
    private NewsAggregationService newsAggregationService;

    @Mock
    private NewsQueryService newsQueryService;

    @Mock
    private MarketDataFacade marketDataFacade;

    @InjectMocks
    private MarketForecastQueryService marketForecastQueryService;

    @Test
    @DisplayName("getCurrentForecastDetail should join related news and optional market data")
    void getCurrentForecastDetail_joinsRelatedNewsAndMarketData() {
        MarketForecastSnapshotDto snapshot = new MarketForecastSnapshotDto(
                MarketMood.SUN,
                "한국어 헤드라인",
                "English headline",
                "한국어 요약",
                "English summary",
                List.of("driver-1"),
                List.of("news-1"),
                List.of("KOSPI rises"),
                Map.of(MacroVariable.KOSPI, ImpactDirection.UP),
                Instant.parse("2026-03-13T00:00:00Z").toString(),
                3
        );
        NewsListItemDto relatedNews = new NewsListItemDto(
                "news-1", "KOSPI rises", "KOSPI rises", "Reuters", Instant.now(), Instant.now(), null, true, true,
                ImpactDirection.UP, SignalSentiment.POSITIVE, "", "summary", 1);
        MarketDataFacade.MarketDataSnapshot marketSnapshot = new MarketDataFacade.MarketDataSnapshot(
                Optional.of(new FxSnapshotDto("USD", "KRW", 1330.0d, Instant.now())),
                Optional.of(new GoldSnapshotDto("USD", 2000.0d, Instant.now())),
                Optional.of(new OilSnapshotDto(78.1d, 82.4d, Instant.now())),
                Optional.of(new IndexSnapshotDto("KOSPI", 2685.4d, Instant.now())),
                Optional.of(new Us10ySnapshotDto(4.21d, LocalDate.parse("2026-03-17"), "FRED", "DGS10")),
                Optional.of(new DxySnapshotDto(103.45d, Instant.now(), "TWELVE_DATA_SYNTHETIC", "ICE_DXY_BASKET", true))
        );

        given(newsAggregationService.getCurrentSnapshot()).willReturn(Optional.of(snapshot));
        given(newsQueryService.getNewsItemsByIds(List.of("news-1"))).willReturn(List.of(relatedNews));
        given(marketDataFacade.getCurrentMarketSnapshot()).willReturn(marketSnapshot);

        var detail = marketForecastQueryService.getCurrentForecastDetail();

        assertThat(detail).isPresent();
        assertThat(detail.get().relatedNewsItems()).containsExactly(relatedNews);
        assertThat(detail.get().fxSnapshot()).isNotNull();
        assertThat(detail.get().goldSnapshot()).isNotNull();
        assertThat(detail.get().oilSnapshot()).isNotNull();
    }
}
