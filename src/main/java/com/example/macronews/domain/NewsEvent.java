package com.example.macronews.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "news_events")
public record NewsEvent(
        @Id String id,
        String title,
        String summary,
        String source,
        String url,
        Instant publishedAt,
        Instant ingestedAt,
        NewsStatus status,
        AnalysisResult analysisResult
) {
}