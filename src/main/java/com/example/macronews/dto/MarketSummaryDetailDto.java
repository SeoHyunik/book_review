package com.example.macronews.dto;

import com.example.macronews.domain.SignalSentiment;
import java.time.Instant;
import java.util.List;

public record MarketSummaryDetailDto(
        String id,
        Instant generatedAt,
        int sourceCount,
        int windowHours,
        String headlineKo,
        String headlineEn,
        String summaryKo,
        String summaryEn,
        String marketViewKo,
        String marketViewEn,
        SignalSentiment dominantSentiment,
        Double confidence,
        List<String> keyDrivers,
        List<MarketSummarySupportingNewsDto> supportingNews
) {
}
