package com.example.macronews.dto.market;

import java.time.Instant;

public record GoldSnapshotDto(
        String baseCurrency,
        double usdPerOunce,
        Instant capturedAt
) {
}
