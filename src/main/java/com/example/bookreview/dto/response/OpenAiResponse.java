package com.example.bookreview.dto.response;

public record OpenAiResponse(String improvedContent, String model, int inputTokens, int outputTokens) {}
