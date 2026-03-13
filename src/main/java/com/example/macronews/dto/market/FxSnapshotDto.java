package com.example.macronews.dto.market;

import java.time.Instant;

public record FxSnapshotDto(
        String baseCurrency,
        String quoteCurrency,
        double rate,
        Instant capturedAt
) {
}
