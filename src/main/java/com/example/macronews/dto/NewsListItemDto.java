package com.example.macronews.dto;

import com.example.macronews.domain.NewsStatus;
import java.time.Instant;

public record NewsListItemDto(
        String id,
        String title,
        String source,
        Instant publishedAt,
        NewsStatus status
) {
}