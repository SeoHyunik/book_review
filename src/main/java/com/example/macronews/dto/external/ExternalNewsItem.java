package com.example.macronews.dto.external;

import java.time.Instant;

public record ExternalNewsItem(
        String externalId,
        String source,
        String title,
        String summary,
        String url,
        Instant publishedAt
) {
}