package com.example.macronews.dto;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.SignalSentiment;
import java.time.Instant;

public record MarketSummarySupportingNewsDto(
        String id,
        String title,
        String source,
        Instant publishedAt,
        ImpactDirection primaryDirection,
        SignalSentiment primarySentiment
) {
}
