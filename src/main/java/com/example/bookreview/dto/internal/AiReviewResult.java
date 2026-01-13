package com.example.bookreview.dto.internal;

public record AiReviewResult(
        String improvedContent,
        boolean fromAi,
        String model,
        String reason) {
}
