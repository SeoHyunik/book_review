package com.example.bookreview.dto.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "news_events")
@Builder
public record NewsEvent(
        @Id String id,
        String externalId,
        String source,
        String title,
        String summary,
        String content,
        String url,
        LocalDateTime publishedAt,
        LocalDateTime ingestedAt,
        NewsStatus status,
        AnalysisResult analysisResult,
        String duplicateOfId,
        String ingestedBy
) {
}
