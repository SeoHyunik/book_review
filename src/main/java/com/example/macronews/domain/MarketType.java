package com.example.macronews.domain;

public enum MarketType {
    KOSPI,
    US_EQUITIES,
    ENERGY_SECTOR,
    TECH_SECTOR;

    public SignalSentiment sentimentFor(ImpactDirection direction) {
        if (direction == null || direction == ImpactDirection.NEUTRAL) {
            return SignalSentiment.NEUTRAL;
        }
        return direction == ImpactDirection.UP ? SignalSentiment.POSITIVE : SignalSentiment.NEGATIVE;
    }
}
