package com.example.bookreview.dto.internal;

// Legacy DTO for review-generation flow. New macro-news AI uses AnalysisResult directly.
public record AiReviewResult(
        String improvedContent,
        boolean fromAi,
        String model,
        String reason,
        int promptTokens,
        int completionTokens,
        int totalTokens) {
}