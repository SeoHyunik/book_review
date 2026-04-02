package com.example.macronews.dto.market;

import java.time.LocalDate;

public record Us10ySnapshotDto(
        double yield,
        LocalDate asOfDate,
        String source,
        String sourceSeries
) {
}
