package com.example.bookreview.dto;

public class OpenAiResult {

    private final String improvedContent;
    private final int promptTokens;
    private final int completionTokens;

    public OpenAiResult(String improvedContent, int promptTokens, int completionTokens) {
        this.improvedContent = improvedContent;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getImprovedContent() {
        return improvedContent;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }
}
