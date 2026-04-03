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
        String displaySource,
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
    public NewsListItemDto(String id, String title, String displayTitle, String source,
            Instant publishedAt, Instant ingestedAt, NewsStatus status,
            boolean hasAnalysis, boolean hasUrl, ImpactDirection primaryDirection,
            SignalSentiment primarySentiment, String macroSummary,
            String interpretationSummary, int priorityScore) {
        this(id, title, displayTitle, source, source, publishedAt, ingestedAt, status,
                hasAnalysis, hasUrl, primaryDirection, primarySentiment, macroSummary, interpretationSummary,
                priorityScore);
    }
}
