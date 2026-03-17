package com.example.macronews.dto;

import com.example.macronews.domain.SignalSentiment;
import java.time.Instant;
import java.util.List;

public record FeaturedMarketSummaryDto(
        String headlineKo,
        String headlineEn,
        String summaryKo,
        String summaryEn,
        Instant generatedAt,
        int sourceCount,
        int windowHours,
        Instant fromPublishedAt,
        Instant toPublishedAt,
        SignalSentiment dominantSentiment,
        List<String> keyDrivers,
        List<String> supportingNewsIds,
        String marketViewKo,
        String marketViewEn,
        Double confidence,
        boolean aiSynthesized,
        String snapshotId
) {
}
