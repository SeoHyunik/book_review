package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
import com.example.macronews.dto.market.GoldSnapshotDto;
import com.example.macronews.dto.market.OilSnapshotDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketDataFacade {

    private final ExchangeRateProvider exchangeRateProvider;
    private final GoldPriceProvider goldPriceProvider;
    private final OilPriceProvider oilPriceProvider;

    public Optional<FxSnapshotDto> getUsdKrw() {
        return exchangeRateProvider.getUsdKrw();
    }

    public Optional<GoldSnapshotDto> getGold() {
        return goldPriceProvider.getGold();
    }

    public Optional<OilSnapshotDto> getOil() {
        return oilPriceProvider.getOil();
    }
}
