package com.example.macronews.dto;

import java.util.List;

public record AutoIngestionBatchStatusDto(
        int requestedCount,
        int returnedCount,
        int ingestedCount,
        int analyzedCount,
        int failedCount,
        int pendingCount,
        boolean completed,
        List<NewsListItemDto> items
) {
}
