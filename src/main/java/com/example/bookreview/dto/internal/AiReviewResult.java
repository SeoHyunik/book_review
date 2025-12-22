package com.example.bookreview.dto.internal;

import java.math.BigDecimal;

public record AiReviewResult(
        String improvedContent,
        String model,
        int promptTokens,
        int completionTokens,
        long totalTokens,
        BigDecimal usdCost) {
}
