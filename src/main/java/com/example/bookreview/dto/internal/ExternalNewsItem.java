package com.example.bookreview.dto.internal;

import java.time.LocalDateTime;

public record ExternalNewsItem(
        String providerItemId,
        String source,
        String title,
        String summary,
        String content,
        String url,
        LocalDateTime publishedAt
) {
}