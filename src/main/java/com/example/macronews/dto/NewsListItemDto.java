package com.example.macronews.dto;

import com.example.macronews.domain.NewsStatus;
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
        String macroSummary,
        String interpretationSummary,
        int priorityScore
) {
}
