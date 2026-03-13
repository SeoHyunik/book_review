package com.example.macronews.dto.market;

import java.time.Instant;

public record IndexSnapshotDto(
        String symbol,
        Double price,
        Instant capturedAt
) {
}
