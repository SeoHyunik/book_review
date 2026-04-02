package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataFacade {

    private final ExchangeRateProvider exchangeRateProvider;
    private final GoldPriceProvider goldPriceProvider;
    private final OilPriceProvider oilPriceProvider;
    private final IndexQuoteProvider indexQuoteProvider;
    private final Us10yProvider us10yProvider;
    private final DxyProvider dxyProvider;

    @Value("${app.market.index.symbol.kospi:}")
    private String kospiSymbol;

    public Optional<FxSnapshotDto> getUsdKrw() {
        return exchangeRateProvider.getUsdKrw();
    }

    public Optional<GoldSnapshotDto> getGold() {
        return goldPriceProvider.getGold();
    }

    public Optional<OilSnapshotDto> getOil() {
        return oilPriceProvider.getOil();
    }

    public Optional<IndexSnapshotDto> getKospi() {
        String symbol = StringUtils.hasText(kospiSymbol) ? kospiSymbol : "KOSPI";
        return indexQuoteProvider.getQuote(symbol);
    }

    public Optional<Us10ySnapshotDto> getUs10y() {
        return us10yProvider.getUs10y();
    }

    public Optional<DxySnapshotDto> getDxy() {
        return dxyProvider.getDxy();
    }

    public MarketDataSnapshot getCurrentMarketSnapshot() {
        try {
            return Mono.zip(
                            loadAsync("USD/KRW", this::getUsdKrw),
                            loadAsync("Gold", this::getGold),
                            loadAsync("Oil", this::getOil),
                            loadAsync("KOSPI", this::getKospi),
                            loadAsync("US10Y", this::getUs10y),
                            loadAsync("DXY", this::getDxy))
                    .map(tuple -> new MarketDataSnapshot(
                            tuple.getT1(),
                            tuple.getT2(),
                            tuple.getT3(),
                            tuple.getT4(),
                            tuple.getT5(),
                            tuple.getT6()))
                    .block();
        } catch (RuntimeException ex) {
            log.warn("[MARKET] parallel aggregation failed; falling back to empty snapshot", ex);
            return MarketDataSnapshot.empty();
        }
    }

    private <T> Mono<Optional<T>> loadAsync(String label, Supplier<Optional<T>> supplier) {
        return Mono.fromCallable(supplier::get)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[MARKET] provider failed label={}", label, ex);
                    return Mono.just(Optional.empty());
                });
    }

    public record MarketDataSnapshot(
            Optional<FxSnapshotDto> usdKrw,
            Optional<GoldSnapshotDto> gold,
            Optional<OilSnapshotDto> oil,
            Optional<IndexSnapshotDto> kospi,
            Optional<Us10ySnapshotDto> us10y,
            Optional<DxySnapshotDto> dxy
    ) {

        public static MarketDataSnapshot empty() {
            return new MarketDataSnapshot(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
    }
}
