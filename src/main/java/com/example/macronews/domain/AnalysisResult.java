package com.example.macronews.domain;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
        String model,
        Instant createdAt,
        List<MacroImpact> macroImpacts,
        List<MarketImpact> marketImpacts
) {
}