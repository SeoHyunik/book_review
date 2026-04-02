package com.example.macronews.dto.market;

import java.time.Instant;

public record DxySnapshotDto(
        double value,
        Instant asOfDateTime,
        String source,
        String sourceSeries,
        boolean synthetic
) {
}
