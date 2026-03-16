package com.example.macronews.dto;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;

public record MarketSignalItemDto(
        MacroVariable variable,
        ImpactDirection direction,
        ImpactDirection sentiment,
        int sampleCount
) {
}
