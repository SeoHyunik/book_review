package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MarketDataFacade {

    private final ExchangeRateProvider exchangeRateProvider;
    private final GoldPriceProvider goldPriceProvider;
    private final OilPriceProvider oilPriceProvider;
    private final IndexQuoteProvider indexQuoteProvider;

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
}
