package com.example.macronews.service.forecast;

import com.example.macronews.dto.NewsListItemDto;
import com.example.macronews.dto.forecast.MarketForecastDetailDto;
import com.example.macronews.dto.forecast.MarketForecastSnapshotDto;
import com.example.macronews.dto.forecast.MarketForecastSummaryHandoffDto;
import com.example.macronews.service.market.MarketDataFacade;
import com.example.macronews.service.news.NewsQueryService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class MarketForecastQueryService {

    private final NewsAggregationService newsAggregationService;
    private final NewsQueryService newsQueryService;
    private final MarketDataFacade marketDataFacade;

    public Optional<MarketForecastSnapshotDto> getCurrentSnapshot() {
        return newsAggregationService.getCurrentSnapshot();
    }

    public Optional<MarketForecastSummaryHandoffDto> getCurrentSummaryHandoff() {
        return getCurrentSnapshot().map(this::toSummaryHandoff);
    }

    public Optional<MarketForecastDetailDto> getCurrentForecastDetail() {
        return getCurrentSnapshot().map(snapshot -> {
            DetailPreparation preparation = Mono.zip(
                            Mono.fromCallable(() -> newsQueryService.getNewsItemsByIds(snapshot.relatedNewsIds()))
                                    .subscribeOn(Schedulers.boundedElastic()),
                            Mono.fromCallable(marketDataFacade::getCurrentMarketSnapshot)
                                    .subscribeOn(Schedulers.boundedElastic()))
                    .map(tuple -> new DetailPreparation(tuple.getT1(), tuple.getT2()))
                    .block();
            DetailPreparation resolvedPreparation = preparation == null ? DetailPreparation.empty() : preparation;
            MarketDataFacade.MarketDataSnapshot marketSnapshot = resolvedPreparation.marketSnapshot();
            return new MarketForecastDetailDto(
                    snapshot,
                    resolvedPreparation.relatedNewsItems(),
                    marketSnapshot.usdKrw().orElse(null),
                    marketSnapshot.gold().orElse(null),
                    marketSnapshot.oil().orElse(null));
        });
    }

    private MarketForecastSummaryHandoffDto toSummaryHandoff(MarketForecastSnapshotDto snapshot) {
        return new MarketForecastSummaryHandoffDto(
                snapshot.mood(),
                snapshot.macroDirections(),
                snapshot.keyDrivers(),
                snapshot.relatedNewsIds(),
                snapshot.analyzedNewsCount(),
                snapshot.generatedAt()
        );
    }

    private record DetailPreparation(
            List<NewsListItemDto> relatedNewsItems,
            MarketDataFacade.MarketDataSnapshot marketSnapshot) {

        private static DetailPreparation empty() {
            return new DetailPreparation(List.of(), MarketDataFacade.MarketDataSnapshot.empty());
        }
    }
}
