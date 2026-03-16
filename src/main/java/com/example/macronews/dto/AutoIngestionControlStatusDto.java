package com.example.macronews.dto;

import java.time.Instant;

public record AutoIngestionControlStatusDto(
        boolean schedulerEnabled,
        boolean runInProgress,
        AutoIngestionRunOutcome latestOutcome,
        Instant latestStartedAt,
        Instant latestCompletedAt,
        Integer latestRequestedCount,
        Integer latestReturnedCount,
        Integer latestAnalyzedCount,
        Integer latestPendingCount,
        Integer latestFailedCount
) {
}
