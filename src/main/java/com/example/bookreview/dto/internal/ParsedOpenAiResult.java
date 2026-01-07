package com.example.bookreview.dto.internal;

import com.example.bookreview.dto.response.OpenAiResponse;

public record ParsedOpenAiResult(
        String improvedContent,
        String model,
        String finishReason,
        int inputTokens,
        int outputTokens) {

    public OpenAiResponse toResponse() {
        return new OpenAiResponse(improvedContent, model, inputTokens, outputTokens);
    }
}
