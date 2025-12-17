package com.example.bookreview.service;

import com.example.bookreview.dto.OpenAiRequest;
import com.example.bookreview.dto.OpenAiResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class OpenAiServiceImpl implements OpenAiService {

    private static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final ExternalApiUtils apiUtils;
    private final Gson gson;
    private final String openAiApiKey;
    private final String openAiUrl;

    public OpenAiServiceImpl(
            ExternalApiUtils apiUtils,
            Gson gson,
            @Value("${openai.api-key}") String openAiApiKey,
            @Value("${openai.api-url:" + DEFAULT_OPENAI_URL + "}") String openAiUrl) {
        this.apiUtils = apiUtils;
        this.gson = gson;
        this.openAiApiKey = openAiApiKey;
        this.openAiUrl = openAiUrl;
    }

    @Override
    public Mono<OpenAiResponse> improveReview(String originalContent) {
        return Mono.fromCallable(() -> executeImproveReview(originalContent));
    }

    private OpenAiResponse executeImproveReview(String originalContent) {
        OpenAiRequest request = new OpenAiRequest(originalContent, openAiApiKey);
        validateRequest(request);

        String payload = buildChatCompletionPayload(request.prompt());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(request.apiKey());

        ExternalApiRequest apiRequest = new ExternalApiRequest(
                HttpMethod.POST,
                headers,
                openAiUrl,
                payload);

        ParsedOpenAiResult parsedResult = callAndParse(apiRequest);
        if (!"stop".equalsIgnoreCase(parsedResult.finishReason())) {
            log.warn("OpenAI response finish_reason was '{}', retrying once", parsedResult.finishReason());
            parsedResult = callAndParse(apiRequest);
        }

        return parsedResult.toResponse();
    }

    private ParsedOpenAiResult callAndParse(ExternalApiRequest apiRequest) {
        ResponseEntity<String> responseEntity = apiUtils.callAPI(apiRequest);
        if (responseEntity == null || !responseEntity.hasBody()) {
            throw new IllegalStateException("OpenAI API response body was empty");
        }
        return parseResponse(responseEntity.getBody());
    }

    private void validateRequest(OpenAiRequest request) {
        Assert.isTrue(StringUtils.hasText(request.apiKey()), "OpenAI API key is not configured");
        Assert.isTrue(StringUtils.hasText(request.prompt()), "Original review content must not be blank");
    }

    private String buildChatCompletionPayload(String originalContent) {
        JsonObject root = new JsonObject();
        root.addProperty("model", "gpt-4o");
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an assistant that improves user book reviews.");

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", originalContent);

        messages.add(systemMessage);
        messages.add(userMessage);
        root.add("messages", messages);
        root.addProperty("temperature", 0.7);

        return gson.toJson(root);
    }

    private ParsedOpenAiResult parseResponse(String responseBody) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root == null) {
            throw new IllegalStateException("Failed to parse OpenAI response body");
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no choices");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("OpenAI response did not include message content");
        }

        String improvedContent = message.get("content").getAsString();
        String finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").getAsString() : "";

        String model = root.has("model") ? root.get("model").getAsString() : "";
        JsonObject usage = root.getAsJsonObject("usage");
        int promptTokens = extractTokenCount(usage, "prompt_tokens");
        int completionTokens = extractTokenCount(usage, "completion_tokens");

        return new ParsedOpenAiResult(improvedContent.trim(), model, finishReason, promptTokens, completionTokens);
    }

    private int extractTokenCount(JsonObject usage, String field) {
        if (usage == null) {
            return 0;
        }
        JsonElement element = usage.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private record ParsedOpenAiResult(
            String improvedContent,
            String model,
            String finishReason,
            int inputTokens,
            int outputTokens) {

        OpenAiResponse toResponse() {
            return new OpenAiResponse(improvedContent, model, inputTokens, outputTokens);
        }
    }
}
