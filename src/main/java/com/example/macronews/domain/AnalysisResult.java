package com.example.macronews.domain;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
        String model,
        Instant createdAt,
        String summaryKo,
        String summaryEn,
        List<MacroImpact> macroImpacts,
        List<MarketImpact> marketImpacts
) {
}
