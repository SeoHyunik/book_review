package com.example.macronews.dto;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.SignalSentiment;

public record MarketSignalItemDto(
        MacroVariable variable,
        ImpactDirection direction,
        SignalSentiment sentiment,
        int sampleCount
) {
}
