package com.example.macronews.dto.market;

import java.time.Instant;

public record OilSnapshotDto(
        Double wtiUsd,
        Double brentUsd,
        Instant capturedAt
) {
}
