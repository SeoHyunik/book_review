package com.example.macronews.service.news;

import com.example.macronews.domain.AnalysisResult;
import com.example.macronews.domain.MacroImpact;
import com.example.macronews.domain.MacroVariable;
import com.example.macronews.domain.MarketImpact;
import com.example.macronews.domain.MarketType;
import com.example.macronews.domain.NewsEvent;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class MarketDriverExtractor {

    private static final int MAX_KEY_DRIVERS = 3;

    List<DriverCount> resolveTopDrivers(List<NewsEvent> recentItems) {
        Map<String, DriverCount> counts = new LinkedHashMap<>();
        for (NewsEvent event : recentItems) {
            AnalysisResult analysisResult = event.analysisResult();
            if (analysisResult == null) {
                continue;
            }
            collectMacroDrivers(counts, analysisResult.macroImpacts());
            collectMarketDrivers(counts, analysisResult.marketImpacts());
        }
        return counts.values().stream()
                .sorted(Comparator.comparingInt(DriverCount::count).reversed()
                        .thenComparing(DriverCount::chipLabel, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_KEY_DRIVERS)
                .toList();
    }

    private void collectMacroDrivers(Map<String, DriverCount> counts, List<MacroImpact> impacts) {
        if (impacts == null) {
            return;
        }
        for (MacroImpact impact : impacts) {
            if (impact == null || impact.variable() == null) {
                continue;
            }
            String key = "macro:" + impact.variable().name();
            counts.compute(key, (ignored, existing) -> existing == null
                    ? new DriverCount(
                    driverChipLabel(impact.variable()),
                    driverLabelKo(impact.variable()),
                    driverLabelEn(impact.variable()),
                    1)
                    : existing.increment());
        }
    }

    private void collectMarketDrivers(Map<String, DriverCount> counts, List<MarketImpact> impacts) {
        if (impacts == null) {
            return;
        }
        for (MarketImpact impact : impacts) {
            if (impact == null || impact.market() == null) {
                continue;
            }
            String key = "market:" + impact.market().name();
            counts.compute(key, (ignored, existing) -> existing == null
                    ? new DriverCount(
                    driverChipLabel(impact.market()),
                    driverLabelKo(impact.market()),
                    driverLabelEn(impact.market()),
                    1)
                    : existing.increment());
        }
    }

    private String driverChipLabel(MacroVariable variable) {
        return driverLabelEn(variable);
    }

    private String driverChipLabel(MarketType marketType) {
        return driverLabelEn(marketType);
    }

    private String driverLabelKo(MacroVariable variable) {
        return switch (variable) {
            case KOSPI -> "\uCF54\uC2A4\uD53C";
            case KOSDAQ -> "\uCF54\uC2A4\uB2E5";
            case VOLATILITY -> "\uBCC0\uB3D9\uC131";
            case OIL -> "\uC720\uAC00";
            case USD -> "\uB2EC\uB7EC";
            case INTEREST_RATE -> "\uAE08\uB9AC";
            case INFLATION -> "\uBB3C\uAC00";
            case GOLD -> "\uAE08";
        };
    }

    private String driverLabelEn(MacroVariable variable) {
        return switch (variable) {
            case KOSPI -> "KOSPI";
            case KOSDAQ -> "KOSDAQ";
            case VOLATILITY -> "Volatility";
            case OIL -> "Oil";
            case USD -> "USD";
            case INTEREST_RATE -> "Interest Rate";
            case INFLATION -> "Inflation";
            case GOLD -> "Gold";
        };
    }

    private String driverLabelKo(MarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "\uCF54\uC2A4\uD53C";
            case US_EQUITIES -> "\uBBF8\uAD6D \uC8FC\uC2DD";
            case ENERGY_SECTOR -> "\uC5D0\uB108\uC9C0 \uC139\uD130";
            case TECH_SECTOR -> "\uAE30\uC220 \uC139\uD130";
        };
    }

    private String driverLabelEn(MarketType marketType) {
        return switch (marketType) {
            case KOSPI -> "KOSPI";
            case US_EQUITIES -> "US Equities";
            case ENERGY_SECTOR -> "Energy Sector";
            case TECH_SECTOR -> "Tech Sector";
        };
    }

    record DriverCount(String chipLabel, String labelKo, String labelEn, int count) {

        private DriverCount increment() {
            return new DriverCount(chipLabel, labelKo, labelEn, count + 1);
        }
    }
}
