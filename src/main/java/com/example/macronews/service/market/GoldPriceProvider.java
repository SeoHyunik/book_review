package com.example.macronews.service.market;

import com.example.macronews.dto.market.GoldSnapshotDto;
import java.util.Optional;

public interface GoldPriceProvider {

    Optional<GoldSnapshotDto> getGold();

    boolean isConfigured();
}
