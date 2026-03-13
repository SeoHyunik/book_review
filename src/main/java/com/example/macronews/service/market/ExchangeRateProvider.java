package com.example.macronews.service.market;

import com.example.macronews.dto.market.FxSnapshotDto;
import java.util.Optional;

public interface ExchangeRateProvider {

    Optional<FxSnapshotDto> getUsdKrw();

    boolean isConfigured();
}
