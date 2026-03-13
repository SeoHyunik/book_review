package com.example.macronews.service.forecast;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastDetailDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketForecastQueryService {

    private final NewsAggregationService newsAggregationService;
    private final NewsQueryService newsQueryService;
    private final MarketDataFacade marketDataFacade;

    public Optional<MarketForecastSnapshotDto> getCurrentSnapshot() {
        return newsAggregationService.getCurrentSnapshot();
    }

    public Optional<MarketForecastDetailDto> getCurrentForecastDetail() {
        return getCurrentSnapshot().map(snapshot -> {
            List<NewsListItemDto> relatedNewsItems = newsQueryService.getNewsItemsByIds(snapshot.relatedNewsIds());
            FxSnapshotDto fxSnapshot = marketDataFacade.getUsdKrw().orElse(null);
            GoldSnapshotDto goldSnapshot = marketDataFacade.getGold().orElse(null);
            OilSnapshotDto oilSnapshot = marketDataFacade.getOil().orElse(null);
            return new MarketForecastDetailDto(snapshot, relatedNewsItems, fxSnapshot, goldSnapshot, oilSnapshot);
        });
    }
}
