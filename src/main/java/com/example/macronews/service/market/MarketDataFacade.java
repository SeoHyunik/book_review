package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.DxySnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.IndexSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import com.example.macronews.dto.market.Us10ySnapshotDto;
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
}
