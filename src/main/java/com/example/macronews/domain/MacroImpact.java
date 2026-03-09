package com.example.macronews.domain;

public record MacroImpact(
        MacroVariable variable,
        ImpactDirection direction,
        double confidence
) {
}