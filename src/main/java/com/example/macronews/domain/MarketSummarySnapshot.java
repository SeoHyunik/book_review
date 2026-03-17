package com.example.macronews.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "market_summary_snapshots")
public record MarketSummarySnapshot(
        @Id String id,
        Instant generatedAt,
        int windowHours,
        int sourceCount,
        Instant fromPublishedAt,
        Instant toPublishedAt,
        String headlineKo,
        String headlineEn,
        String summaryKo,
        String summaryEn,
        String marketViewKo,
        String marketViewEn,
        SignalSentiment dominantSentiment,
        List<String> keyDrivers,
        List<String> supportingNewsIds,
        Double confidence,
        boolean valid,
        boolean aiSynthesized,
        String model
) {
}
