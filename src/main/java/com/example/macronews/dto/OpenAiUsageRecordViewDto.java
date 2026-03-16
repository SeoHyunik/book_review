package com.example.macronews.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OpenAiUsageRecordViewDto(
        Instant timestamp,
        String model,
        String featureMessageKey,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal estimatedUsdCost,
        BigDecimal estimatedKrwCost
) {
}
