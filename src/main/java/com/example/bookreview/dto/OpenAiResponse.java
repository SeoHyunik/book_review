package com.example.bookreview.dto;

public record OpenAiResponse(String improvedContent, String model, int inputTokens, int outputTokens) {}
