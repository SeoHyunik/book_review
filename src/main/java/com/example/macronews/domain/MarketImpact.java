package com.example.macronews.domain;

public record MarketImpact(
        MarketType market,
        ImpactDirection direction,
        double confidence
) {
}