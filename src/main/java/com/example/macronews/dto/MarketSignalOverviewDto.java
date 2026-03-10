package com.example.macronews.dto;

import java.util.List;

public record MarketSignalOverviewDto(
        List<MarketSignalItemDto> items
) {
    public boolean hasSignals() {
        return items != null && !items.isEmpty();
    }
}
