package com.example.macronews.dto.forecast;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketMood;
import java.util.List;
import java.util.Map;

public record MarketForecastSnapshotDto(
        MarketMood mood,
        String headlineKo,
        String headlineEn,
        String summaryKo,
        String summaryEn,
        List<String> keyDrivers,
        List<String> relatedNewsIds,
        List<String> relatedNewsTitles,
        Map<MacroVariable, ImpactDirection> macroDirections,
        String generatedAt,
        int analyzedNewsCount
) {
}
