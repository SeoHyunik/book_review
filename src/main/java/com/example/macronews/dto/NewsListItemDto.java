package com.example.macronews.dto;

import com.example.macronews.domain.ImpactDirection;
import com.example.macronews.domain.NewsStatus;
import com.example.macronews.domain.SignalSentiment;
import java.time.Instant;

public record NewsListItemDto(
        String id,
        String title,
        String displayTitle,
        String source,
        Instant publishedAt,
        Instant ingestedAt,
        NewsStatus status,
        boolean hasAnalysis,
        boolean hasUrl,
        ImpactDirection primaryDirection,
        SignalSentiment primarySentiment,
        String macroSummary,
        String interpretationSummary,
        int priorityScore
) {
}
