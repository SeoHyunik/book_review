package com.example.macronews.dto;

import java.math.BigDecimal;

public record OpenAiUsageAggregateDto(
        String label,
        long requestCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal estimatedUsdCost,
        BigDecimal estimatedKrwCost
) {
}
