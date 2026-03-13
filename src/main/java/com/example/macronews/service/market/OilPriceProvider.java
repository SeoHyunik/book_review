package com.example.macronews.service.market;

import com.example.macronews.dto.market.OilSnapshotDto;
import java.util.Optional;

public interface OilPriceProvider {

    Optional<OilSnapshotDto> getOil();

    boolean isConfigured();
}
