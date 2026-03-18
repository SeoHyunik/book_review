package com.example.macronews.dto.forecast;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import java.util.List;
import java.util.Map;

public record MarketForecastSummaryHandoffDto(
        MarketMood mood,
        Map<MacroVariable, ImpactDirection> macroDirections,
        List<String> keyDrivers,
        List<String> relatedNewsIds,
        int analyzedNewsCount,
        String generatedAt
) {
}
