package com.example.bookreview.service;

import com.example.bookreview.dto.OpenAiResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.usage.Usage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OpenAiServiceImpl implements OpenAiService {

    private final com.theokanning.openai.service.OpenAiService openAiClient;
    private final String openAiApiKey;

    public OpenAiServiceImpl(
            com.theokanning.openai.service.OpenAiService openAiClient,
            @Value("${openai.api-key}") String openAiApiKey) {
        this.openAiClient = openAiClient;
        this.openAiApiKey = openAiApiKey;
    }

    @Override
    public OpenAiResult improveReview(String originalContent) {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }
        if (!StringUtils.hasText(originalContent)) {
            throw new IllegalArgumentException("Original review content must not be blank");
        }

        String prompt = buildPrompt(originalContent);
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o")
                .messages(List.of(userMessage))
                .temperature(0.7)
                .build();

        try {
            ChatCompletionResult response = openAiClient.createChatCompletion(request);
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new IllegalStateException("OpenAI returned an empty response while improving review");
            }

            String improvedContent = response.getChoices().get(0).getMessage().getContent();
            if (!StringUtils.hasText(improvedContent)) {
                throw new IllegalStateException("OpenAI response did not contain improved content");
            }

            Usage usage = response.getUsage();
            int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

            return new OpenAiResult(improvedContent.trim(), promptTokens, completionTokens);
        } catch (Exception exception) {
            log.error("Failed to call OpenAI Chat Completion API", exception);
            throw new IllegalStateException("Failed to improve review with OpenAI", exception);
        }
    }

    private String buildPrompt(String originalContent) {
        return "You are an expert book review editor. Improve the following review by removing redundancies, "
                + "enhancing coherence, and preserving the original meaning. Provide only the improved review text."
                + "\n\nOriginal Review:\n" + originalContent + "\n\nImproved Review:";
    }
}
